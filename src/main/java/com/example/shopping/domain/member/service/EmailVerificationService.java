package com.example.shopping.domain.member.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import com.example.shopping.global.validation.ContactFormats;

/**
 * 이메일 인증번호. 메일로 보낸 인증번호(10분 유효)를 확인한다.
 * 회원가입은 맞히면 가입 요청에 넣을 확인 토큰(30분 유효)을 준다. 비밀번호 재설정은 PasswordResetService가 쓴다.
 * 인증번호는 5번 틀리면 다시 받아야 하고, 1분에 한 번만 다시 보낼 수 있다.
 * 같은 이메일·용도로는 24시간 동안 5번까지 보내고, 그동안 틀린 횟수는 모두 합쳐 10번까지 받는다.
 * IP 하나로도 1분마다 새 번호를 받아 하루 수천 번 넣어 보거나, 한 사람에게 메일을 끝없이 보내는 것을 막는다.
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
	private final String serviceName;

	public EmailVerificationService(EmailVerificationRepository verificationRepository,
			MemberRepository memberRepository, EmailSender emailSender, TransactionTemplate transactionTemplate,
			@Value("${app.mail.service-name:이음 쇼핑몰}") String serviceName) {
		this.verificationRepository = verificationRepository;
		this.memberRepository = memberRepository;
		this.emailSender = emailSender;
		this.transactionTemplate = transactionTemplate;
		this.serviceName = serviceName;
	}

	/** 이미 가입된 이메일이면 409. 메일 서버가 받지 않으면 503이고, 이때는 바로 다시 요청할 수 있다. */
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

					10분 안에 입력해 주세요. 직접 요청하지 않았다면 이 메일은 무시해 주세요.
					""".formatted(code));
		} catch (EmailSendException e) {
			verificationRepository.deleteById(verification.getId());
			throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
		}
		return new EmailVerificationSendResponse(verification.getExpiresAt());
	}

	/** 인증번호가 맞으면 가입 요청(emailVerificationToken)에 넣을 확인 토큰을 준다. 틀린 횟수는 롤백하지 않는다. */
	@Transactional(noRollbackFor = BusinessException.class)
	public EmailVerificationConfirmResponse confirmSignupCode(String rawEmail, String code) {
		EmailVerification verification = verifyCode(ContactFormats.loginEmail(rawEmail), VerificationPurpose.SIGNUP, code);
		String token = VerificationCodes.newToken();
		LocalDateTime tokenExpiresAt = LocalDateTime.now().plus(TOKEN_TTL);
		verification.issueToken(VerificationCodes.hash(token), tokenExpiresAt);
		return new EmailVerificationConfirmResponse(token, tokenExpiresAt);
	}

	/**
	 * 회원가입 때 확인 토큰을 한 번 쓴다. 가입 저장과 같은 트랜잭션에서 불러야 가입이 실패하면 토큰도 다시 쓸 수 있다.
	 * 다른 이메일로 받은 토큰이거나 만료·사용된 토큰이면 400.
	 */
	public void consumeSignupToken(String email, String token) {
		LocalDateTime now = LocalDateTime.now();
		EmailVerification verification = verificationRepository
				.findByTokenHashAndPurpose(VerificationCodes.hash(token), VerificationPurpose.SIGNUP)
				.filter(v -> v.getEmail().equals(email) && v.isTokenUsable(now))
				.orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_VERIFICATION_REQUIRED));
		verification.consume(now);
	}

	/**
	 * 같은 이메일·용도의 이전 인증번호는 더 쓸 수 없게 하고 새 인증번호를 저장한다.
	 * 1분 안에 다시 요청하면 429(VERIFICATION_RESEND_TOO_SOON), 24시간 동안 5번을 넘으면 429(VERIFICATION_DAILY_LIMIT_EXCEEDED).
	 */
	EmailVerification issue(String email, VerificationPurpose purpose, String code) {
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
		return verificationRepository.save(EmailVerification.create(email, purpose,
				VerificationCodes.hash(purpose.name(), email, code), now.plus(CODE_TTL)));
	}

	/**
	 * 가장 최근에 보낸 인증번호와 비교한다. 틀리면 횟수를 올리고, 5번 틀리면 다시 받아야 한다.
	 * 24시간 동안 틀린 횟수가 모두 합쳐 10번이면 맞는 번호도 받지 않는다(429).
	 * 인증번호가 없는 경우와 만료된 경우, 틀린 경우를 구분해 알려 주지 않는다.
	 */
	EmailVerification verifyCode(String email, VerificationPurpose purpose, String code) {
		EmailVerification verification = verificationRepository
				.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc(email, purpose)
				.orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID));
		if (verification.isAttemptsExceeded()) {
			throw new BusinessException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
		}
		LocalDateTime now = LocalDateTime.now();
		if (verificationRepository.sumAttemptsSince(email, purpose, now.minus(DAILY_WINDOW)) >= DAILY_FAILURE_LIMIT) {
			throw new BusinessException(ErrorCode.VERIFICATION_DAILY_LIMIT_EXCEEDED);
		}
		if (!verification.isCodeUsable(now)) {
			throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID);
		}
		String actual = VerificationCodes.hash(purpose.name(), email, code);
		if (!VerificationCodes.matches(verification.getCodeHash(), actual)) {
			verification.recordFailedAttempt();
			throw new BusinessException(verification.isAttemptsExceeded() ? ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED
					: ErrorCode.VERIFICATION_CODE_INVALID);
		}
		return verification;
	}

	String subject(String title) {
		return "[" + serviceName + "] " + title;
	}
}
