package com.example.shopping.domain.member.service;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.shopping.domain.member.entity.EmailVerification;
import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.domain.member.entity.VerificationPurpose;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.mail.BackgroundEmailSender;
import com.example.shopping.global.security.MemberTokenStateCache;
import com.example.shopping.global.validation.ContactFormats;

import lombok.RequiredArgsConstructor;

/**
 * 비밀번호를 잊은 회원이 이메일 인증번호로 비밀번호를 다시 정한다.
 * 가입 여부를 알아낼 수 없도록, 인증번호 요청은 가입되지 않은 이메일이어도 똑같이 성공으로 응답하고 메일은 응답과 따로 보낸다.
 * 재설정하면 다른 기기의 로그인(Refresh 토큰)과 이미 발급된 Access 토큰을 모두 끊는다.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

	private final EmailVerificationService verificationService;
	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenService refreshTokenService;
	private final MemberTokenStateCache memberTokenStateCache;
	private final LoginAttemptLimiter loginAttemptLimiter;
	private final BackgroundEmailSender backgroundEmailSender;
	private final TransactionTemplate transactionTemplate;

	/**
	 * 사용 중인 계정(ACTIVE)일 때만 인증번호를 보낸다. 1분 안에 다시 요청하거나 24시간 동안 5통을 넘으면 보내지 않는다.
	 * 응답은 언제나 같다.
	 */
	public void requestReset(String rawEmail) {
		String email = ContactFormats.loginEmail(rawEmail);
		String code = VerificationCodes.newCode();
		boolean issued = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
			if (findActive(email) == null) {
				return false;
			}
			try {
				verificationService.issue(email, VerificationPurpose.PASSWORD_RESET, code);
				return true;
			} catch (BusinessException sendLimited) {
				return false;
			}
		}));
		if (issued) {
			backgroundEmailSender.send(email, verificationService.subject("비밀번호 재설정 인증번호"), """
					비밀번호 재설정 인증번호입니다.

					인증번호: %s

					10분 안에 입력해 주세요. 직접 요청하지 않았다면 이 메일은 무시해 주세요. 비밀번호는 바뀌지 않습니다.
					""".formatted(code));
		}
	}

	/** 인증번호가 맞으면 비밀번호를 바꾼다. 비밀번호 해시 계산 동안에는 DB 커넥션을 잡지 않는다. */
	public void resetPassword(String rawEmail, String code, String newPassword) {
		String email = ContactFormats.loginEmail(rawEmail);
		String passwordHash = passwordEncoder.encode(newPassword);
		BusinessException failure = transactionTemplate.execute(status -> {
			try {
				EmailVerification verification = verificationService.verifyCode(email,
						VerificationPurpose.PASSWORD_RESET, code);
				Member member = findActive(email);
				if (member == null) {
					throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID);
				}
				verification.consume(LocalDateTime.now());
				member.changePassword(passwordHash);
				refreshTokenService.revokeAll(member.getId());
				memberTokenStateCache.refresh(member);
				return null;
			} catch (BusinessException e) {
				// 틀린 횟수는 커밋해 남기고, 응답만 실패로 돌려준다.
				return e;
			}
		});
		if (failure != null) {
			throw failure;
		}
		// 본인이 비밀번호를 새로 정했으므로 로그인 실패로 막힌 상태도 푼다.
		loginAttemptLimiter.reset(email);
	}

	private Member findActive(String email) {
		return memberRepository.findByEmail(email).filter(m -> m.getStatus() == MemberStatus.ACTIVE).orElse(null);
	}
}
