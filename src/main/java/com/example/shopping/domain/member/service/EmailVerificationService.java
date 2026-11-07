package com.example.shopping.domain.member.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.shopping.domain.member.dto.response.EmailVerificationConfirmResponse;
import com.example.shopping.domain.member.dto.response.EmailVerificationSendResponse;
import com.example.shopping.domain.member.entity.EmailVerification;
import com.example.shopping.domain.member.entity.VerificationPurpose;
import com.example.shopping.domain.member.repository.EmailVerificationRepository;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.mail.EmailSender;
import com.example.shopping.global.mail.EmailSender.EmailSendException;
import com.example.shopping.global.security.KeyedHash;
import com.example.shopping.global.security.SecretHashes;
import com.example.shopping.global.validation.ContactFormats;

/**
 * 이메일 인증번호. 메일로 보낸 인증번호(영문 대소문자·숫자 6자리, 10분 유효)를 확인한다.
 * 회원가입은 맞히면 가입 요청에 넣을 확인 토큰(30분 유효)을 준다. 비밀번호 재설정은 PasswordResetService가 쓴다.
 * 인증번호는 1분에 한 번만 다시 보낼 수 있고, 같은 이메일·용도로는 24시간 동안 5번까지 보낸다.
 * 틀린 횟수는 VerificationAttemptGuard가 센다(3번이면 로봇 확인, 5번이면 1시간 차단).
 * 인증번호 하나는 5번 틀리면 다시 받아야 하고, 24시간 동안 틀린 횟수는 모두 합쳐 10번까지 받는다.
 * 인증번호는 서버 비밀 키를 섞어 해시하므로 DB 값만으로는 알아낼 수 없다.
 */
@Service
public class EmailVerificationService {

	static final Duration CODE_TTL = Duration.ofMinutes(10);
	static final Duration TOKEN_TTL = Duration.ofMinutes(30);
	static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
	static final Duration DAILY_WINDOW = Duration.ofDays(1);
	static final int DAILY_SEND_LIMIT = 5;
	static final int DAILY_FAILURE_LIMIT = 10;

	private final EmailVerificationRepository verificationRepository;
	private final MemberRepository memberRepository;
	private final EmailSender emailSender;
	private final TransactionTemplate transactionTemplate;
	private final VerificationAttemptGuard attemptGuard;
	private final KeyedHash codeHashes;
	private final KeyedHash tokenHashes;
	private final String serviceName;

	public EmailVerificationService(EmailVerificationRepository verificationRepository,
			MemberRepository memberRepository, EmailSender emailSender, TransactionTemplate transactionTemplate,
			VerificationAttemptGuard attemptGuard, SecretHashes secretHashes,
			@Value("${app.mail.service-name:이음 쇼핑몰}") String serviceName) {
		this.verificationRepository = verificationRepository;
		this.memberRepository = memberRepository;
		this.emailSender = emailSender;
		this.transactionTemplate = transactionTemplate;
		this.attemptGuard = attemptGuard;
		this.codeHashes = secretHashes.forPurpose(SecretHashes.EMAIL_VERIFICATION_CODE);
		this.tokenHashes = secretHashes.forPurpose(SecretHashes.EMAIL_VERIFICATION_TOKEN);
		this.serviceName = serviceName;
	}

	/**
	 * 이미 가입된 이메일이면 409, 인증번호를 여러 번 틀려 막힌 이메일이면 429.
	 * 메일 서버가 받지 않으면 503이고, 이때는 바로 다시 요청할 수 있다.
	 */
	public EmailVerificationSendResponse sendSignupCode(String rawEmail) {
		String email = ContactFormats.loginEmail(rawEmail);
		if (memberRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
		String code = VerificationCodes.newCode();
		EmailVerification verification = transactionTemplate
				.execute(status -> issue(email, VerificationPurpose.SIGNUP, code));
		try {
			emailSender.send(email, subject("이메일 인증번호"), """
					회원가입 이메일 인증번호입니다.

					인증번호: %s

					영문 대소문자를 구분해 10분 안에 입력해 주세요. 직접 요청하지 않았다면 이 메일은 무시해 주세요.
					""".formatted(code));
		} catch (EmailSendException e) {
			verificationRepository.deleteById(verification.getId());
			throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
		}
		return new EmailVerificationSendResponse(verification.getExpiresAt());
	}

	/**
	 * 인증번호가 맞으면 가입 요청(emailVerificationToken)에 넣을 확인 토큰을 준다. 틀린 횟수는 롤백하지 않는다.
	 * 로봇 확인은 DB 트랜잭션을 열기 전에 끝낸다(확인 서비스를 기다리는 동안 DB 커넥션을 잡지 않는다).
	 */
	public EmailVerificationConfirmResponse confirmSignupCode(String rawEmail, String code, String captchaToken,
			String remoteIp) {
		String email = ContactFormats.loginEmail(rawEmail);
		attemptGuard.checkAttemptAllowed(email, VerificationPurpose.SIGNUP, captchaToken, remoteIp);
		String token = VerificationCodes.newToken();
		LocalDateTime tokenExpiresAt = LocalDateTime.now().plus(TOKEN_TTL);
		BusinessException failure = transactionTemplate.execute(status -> {
			try {
				verifyCode(email, VerificationPurpose.SIGNUP, code)
						.issueToken(tokenHashes.hash(token), tokenExpiresAt);
				return null;
			} catch (BusinessException e) {
				// 틀린 횟수는 커밋해 남기고, 응답만 실패로 돌려준다.
				return e;
			}
		});
		if (failure != null) {
			throw failure;
		}
		attemptGuard.reset(email, VerificationPurpose.SIGNUP);
		return new EmailVerificationConfirmResponse(token, tokenExpiresAt);
	}

	/**
	 * 회원가입 때 확인 토큰을 한 번 쓴다. 가입 저장과 같은 트랜잭션에서 불러야 가입이 실패하면 토큰도 다시 쓸 수 있다.
	 * 다른 이메일로 받은 토큰이거나 만료·사용된 토큰이면 400.
	 */
	public void consumeSignupToken(String email, String token) {
		LocalDateTime now = LocalDateTime.now();
		EmailVerification verification = verificationRepository
				.findByTokenHashAndPurpose(tokenHashes.hash(token), VerificationPurpose.SIGNUP)
				.filter(v -> v.getEmail().equals(email) && v.isTokenUsable(now))
				.orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_VERIFICATION_REQUIRED));
		verification.consume(now);
	}

	/**
	 * 같은 이메일·용도의 이전 인증번호는 더 쓸 수 없게 하고 새 인증번호를 저장한다.
	 * 인증번호를 여러 번 틀려 막힌 동안은 429(VERIFICATION_LOCKED),
	 * 1분 안에 다시 요청하면 429(VERIFICATION_RESEND_TOO_SOON), 24시간 동안 5번을 넘으면 429(VERIFICATION_DAILY_LIMIT_EXCEEDED).
	 */
	EmailVerification issue(String email, VerificationPurpose purpose, String code) {
		attemptGuard.checkNotLocked(email, purpose);
		LocalDateTime now = LocalDateTime.now();
		verificationRepository.findFirstByEmailAndPurposeOrderByIdDesc(email, purpose)
				.filter(latest -> latest.getCreatedAt().isAfter(now.minus(RESEND_INTERVAL)))
				.ifPresent(latest -> {
					throw new BusinessException(ErrorCode.VERIFICATION_RESEND_TOO_SOON);
				});
		if (verificationRepository.countByEmailAndPurposeAndCreatedAtAfter(email, purpose,
				now.minus(DAILY_WINDOW)) >= DAILY_SEND_LIMIT) {
			throw new BusinessException(ErrorCode.VERIFICATION_DAILY_LIMIT_EXCEEDED);
		}
		verificationRepository.consumeAll(email, purpose, now);
		return verificationRepository.save(EmailVerification.create(email, purpose, codeHash(purpose, email, code),
				now.plus(CODE_TTL)));
	}

	/**
	 * 가장 최근에 보낸 인증번호와 비교한다. 틀리면 횟수를 올리고, 5번 틀리면 다시 받아야 한다.
	 * 24시간 동안 틀린 횟수가 모두 합쳐 10번이면 맞는 번호도 받지 않는다(429).
	 * 인증번호가 없는 경우와 만료된 경우, 틀린 경우를 구분해 알려 주지 않고 모두 틀린 횟수로 센다.
	 * 이번 실패로 VerificationAttemptGuard의 한도에 닿으면 VERIFICATION_LOCKED(429)로 알린다.
	 * 막힘·로봇 확인은 호출하는 쪽이 트랜잭션 밖에서 먼저 확인한다(VerificationAttemptGuard.checkAttemptAllowed).
	 */
	EmailVerification verifyCode(String email, VerificationPurpose purpose, String code) {
		EmailVerification verification = verificationRepository
				.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc(email, purpose)
				.orElse(null);
		if (verification == null) {
			throw failure(email, purpose, ErrorCode.VERIFICATION_CODE_INVALID);
		}
		if (verification.isAttemptsExceeded()) {
			throw failure(email, purpose, ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
		}
		LocalDateTime now = LocalDateTime.now();
		if (verificationRepository.sumAttemptsSince(email, purpose, now.minus(DAILY_WINDOW)) >= DAILY_FAILURE_LIMIT) {
			throw new BusinessException(ErrorCode.VERIFICATION_DAILY_LIMIT_EXCEEDED);
		}
		if (!verification.isCodeUsable(now)) {
			throw failure(email, purpose, ErrorCode.VERIFICATION_CODE_INVALID);
		}
		if (!codeHashes.matches(verification.getCodeHash(), purpose.name(), email, code)) {
			verification.recordFailedAttempt();
			throw failure(email, purpose, verification.isAttemptsExceeded() ? ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED
					: ErrorCode.VERIFICATION_CODE_INVALID);
		}
		return verification;
	}

	String subject(String title) {
		return "[" + serviceName + "] " + title;
	}

	/** 같은 인증번호라도 이메일·용도가 다르면 해시가 달라진다. 서버 비밀 키가 없으면 DB 값으로 인증번호를 맞혀 볼 수 없다. */
	private String codeHash(VerificationPurpose purpose, String email, String code) {
		return codeHashes.hash(purpose.name(), email, code);
	}

	/** 틀린 횟수를 세고, 이번에 막혔으면 VERIFICATION_LOCKED를 돌려준다. */
	private BusinessException failure(String email, VerificationPurpose purpose, ErrorCode errorCode) {
		boolean locked = attemptGuard.recordFailure(email, purpose);
		return new BusinessException(locked ? ErrorCode.VERIFICATION_LOCKED : errorCode);
	}
}
