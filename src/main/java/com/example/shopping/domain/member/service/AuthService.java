package com.example.shopping.domain.member.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.audit.service.AdminAuditLogService;
import com.example.shopping.domain.consent.service.ConsentService;
import com.example.shopping.domain.member.dto.AuthTokens;
import com.example.shopping.domain.member.dto.request.LoginRequest;
import com.example.shopping.domain.member.dto.request.SignupRequest;
import com.example.shopping.domain.member.dto.response.SignupResponse;
import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberRole;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.domain.member.entity.RefreshToken;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.security.AdminSecurityProperties;
import com.example.shopping.global.security.JwtTokenProvider;
import com.example.shopping.global.validation.ContactFormats;

/**
 * 비밀번호 해시 계산이 느리므로 그동안 DB 커넥션을 잡고 있지 않도록
 * 메서드 전체를 트랜잭션으로 묶지 않고 리포지토리 호출 단위로만 DB에 접근한다.
 */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	// 관리자 로그인 감사 로그의 요약
	private static final String ADMIN_LOGIN_SUCCEEDED = "관리자 로그인 성공";
	private static final String ADMIN_LOGIN_ENROLLED = "관리자 OTP 등록·로그인 성공";
	private static final String ADMIN_LOGIN_FAILED = "관리자 로그인 실패";
	private static final String ADMIN_PASSWORD_VERIFIED = "관리자 비밀번호 확인(OTP 대기)";
	private static final String ADMIN_OTP_FAILED = "관리자 OTP 확인 실패";
	private static final String ADMIN_MEMBER_LOGIN_REJECTED = "관리자 계정 회원 로그인 시도(거절)";

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailVerificationService emailVerificationService;
	private final TransactionTemplate transactionTemplate;
	private final boolean signupEmailVerificationRequired;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenService refreshTokenService;
	private final AccessTokenService accessTokenService;
	private final AdminAuditLogService auditLogService;
	private final LoginAttemptLimiter loginAttemptLimiter;
	private final ConsentService consentService;
	private final AdminMfaService adminMfaService;
	private final AdminSecurityProperties adminSecurity;

	// 없는 이메일로 로그인해도 해시 비교를 한 번 수행해, 응답 시간으로 가입 여부를 추측하지 못하게 한다.
	private final String dummyPasswordHash;

	public AuthService(EmailVerificationService emailVerificationService, TransactionTemplate transactionTemplate,
			@org.springframework.beans.factory.annotation.Value("${app.auth.signup-email-verification-required:false}")
			boolean signupEmailVerificationRequired,
			MemberRepository memberRepository, PasswordEncoder passwordEncoder,
			JwtTokenProvider jwtTokenProvider, RefreshTokenService refreshTokenService,
			AccessTokenService accessTokenService, AdminAuditLogService auditLogService,
			LoginAttemptLimiter loginAttemptLimiter, ConsentService consentService, AdminMfaService adminMfaService,
			AdminSecurityProperties adminSecurity) {
		this.memberRepository = memberRepository;
		this.passwordEncoder = passwordEncoder;
		this.emailVerificationService = emailVerificationService;
		this.transactionTemplate = transactionTemplate;
		this.signupEmailVerificationRequired = signupEmailVerificationRequired;
		this.jwtTokenProvider = jwtTokenProvider;
		this.refreshTokenService = refreshTokenService;
		this.accessTokenService = accessTokenService;
		this.auditLogService = auditLogService;
		this.loginAttemptLimiter = loginAttemptLimiter;
		this.consentService = consentService;
		this.adminMfaService = adminMfaService;
		this.adminSecurity = adminSecurity;
		this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	/**
	 * 이메일 인증 토큰을 주면 인증을 확인하고 한 번 쓴다. 인증이 필수(SIGNUP_EMAIL_VERIFICATION_REQUIRED=true)인데 없으면 400.
	 * 토큰 사용과 가입 저장은 한 트랜잭션이라 가입이 실패하면 토큰을 다시 쓸 수 있다.
	 * 토큰을 주면 이미 가입된 이메일인지(409)는 토큰을 확인한 뒤에만 알려 준다. 그래서 인증을 필수로 하면 메일함 주인만
	 * 가입 여부를 알 수 있다(#140). 인증이 필수가 아니면 토큰 없는 가입 요청의 409로 가입 여부를 알 수 있다.
	 */
	public SignupResponse signup(SignupRequest request) {
		String email = ContactFormats.loginEmail(request.email());
		String token = request.emailVerificationToken();
		boolean withToken = StringUtils.hasText(token);
		if (signupEmailVerificationRequired && !withToken) {
			throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
		}
		if (!withToken && memberRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}

		String phone = ContactFormats.phone(request.phone());
		Member member = Member.create(email, passwordEncoder.encode(request.password()), request.name().trim(), phone);
		try {
			return transactionTemplate.execute(status -> {
				if (withToken) {
					emailVerificationService.consumeSignupToken(email, token);
					if (memberRepository.existsByEmail(email)) {
						throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
					}
					member.markEmailVerified(LocalDateTime.now());
				}
				Member saved = memberRepository.saveAndFlush(member);
				consentService.recordSignup(saved.getId(), Boolean.TRUE.equals(request.marketingAgreed()));
				return SignupResponse.from(saved);
			});
		} catch (DataIntegrityViolationException e) {
			// 같은 이메일로 동시에 가입 요청이 들어오면 위의 중복 검사를 둘 다 통과하므로 유니크 제약이 최종 방어선이다.
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
	}

	/**
	 * 회원 로그인. 관리자 계정은 받지 않는다(#163).
	 *
	 * @param clientIp 서버가 확인한 클라이언트 IP. 로봇 확인 서비스가 토큰을 받은 IP와 비교한다.
	 */
	public AuthTokens login(LoginRequest request, String clientIp) {
		return issueTokens(authenticate(request, clientIp, false));
	}

	/**
	 * 관리자 로그인 1단계(비밀번호). 일반 회원 계정이면 계정이 없는 것과 똑같이 응답한다.
	 * OTP를 요구하면(#164) 토큰 대신 OTP 확인용 1회용 토큰을 주고, 로그인은 verifyAdminMfa에서 끝난다.
	 * 각 단계의 성공·실패를 감사 로그에 남긴다. 기록에 실패해도 로그인 결과는 바꾸지 않는다.
	 */
	public AdminLoginResult adminLogin(LoginRequest request, String clientIp) {
		Member admin = authenticate(request, clientIp, true);
		if (adminMfaService.isRequiredFor(admin)) {
			AdminMfaService.Challenge challenge = adminMfaService.start(admin);
			recordAdminLogin(admin.getId(), clientIp, null, 200, ADMIN_PASSWORD_VERIFIED);
			return new AdminLoginResult(null, challenge);
		}
		AuthTokens tokens = issueAdminTokens(admin, newAdminSessionExpiry());
		recordAdminLogin(admin.getId(), clientIp, null, 200, ADMIN_LOGIN_SUCCEEDED);
		return new AdminLoginResult(tokens, null);
	}

	/** 관리자 로그인 2단계(OTP). 코드를 확인하고 토큰을 준다. 처음 넣은 코드면 OTP 등록도 이때 끝난다. */
	public AuthTokens verifyAdminMfa(String mfaToken, String code, String clientIp) {
		Member admin = adminMfaService.challengedAdmin(mfaToken);
		boolean enrolled;
		try {
			enrolled = adminMfaService.verify(mfaToken, code);
		} catch (BusinessException e) {
			recordAdminLogin(null, clientIp, admin.getEmail(), e.getErrorCode().getStatus().value(), ADMIN_OTP_FAILED);
			throw e;
		}
		AuthTokens tokens = issueAdminTokens(admin, newAdminSessionExpiry());
		recordAdminLogin(admin.getId(), clientIp, null, 200, enrolled ? ADMIN_LOGIN_ENROLLED : ADMIN_LOGIN_SUCCEEDED);
		return tokens;
	}

	private void recordAdminLogin(Long memberId, String clientIp, String email, int status, String summary) {
		try {
			auditLogService.recordAdminLogin(memberId, clientIp, email, status, summary);
		} catch (RuntimeException e) {
			log.error("관리자 로그인 감사 로그 저장 실패: member={}, ip={}, status={}", memberId, clientIp, status, e);
		}
	}

	/** 회원 Refresh 토큰으로 Access 토큰을 다시 발급한다. Refresh 토큰도 새것으로 교체된다. */
	public AuthTokens refresh(String refreshToken) {
		return refresh(refreshToken, false);
	}

	/** 관리자 Refresh 토큰으로 Access 토큰을 다시 발급한다. Refresh 토큰도 새것으로 교체된다. */
	public AuthTokens adminRefresh(String refreshToken) {
		return refresh(refreshToken, true);
	}

	/**
	 * 회원 재발급은 관리자 토큰을, 관리자 재발급은 회원 토큰을 주지 않는다(#163). 관리자 계정은 관리자 로그인으로만 들어오고,
	 * 권한을 바꾸면 토큰 버전이 올라 이전 Refresh 토큰을 쓸 수 없으므로, 토큰 주인의 지금 권한이 그 세션의 종류다.
	 */
	private AuthTokens refresh(String refreshToken, boolean adminSession) {
		RefreshToken consumed = refreshTokenService.consume(refreshToken);
		Member member = memberRepository.findById(consumed.getMemberId())
				.filter(m -> m.getStatus() == MemberStatus.ACTIVE)
				.filter(m -> m.getRole().canAccessAdminApi() == adminSession)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		if (consumed.getTokenVersion() != member.getAccessTokenVersion()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		if (!adminSession) {
			return issueTokens(member);
		}
		// 관리자 세션은 로그인할 때 정한 종료 시각을 넘기지 못한다(#164). 이 값이 없는 토큰(#164 전에 받은 것)은 받지 않는다.
		if (consumed.getSessionExpiresAt() == null) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		return issueAdminTokens(member, consumed.getSessionExpiresAt());
	}

	/** Refresh 토큰과 요청에 포함된 유효한 Access 토큰을 폐기한다. */
	public void logout(String refreshToken, String authorizationHeader) {
		accessTokenService.revokeIfValid(authorizationHeader);
		refreshTokenService.revoke(refreshToken);
	}

	/**
	 * 회원 로그인 API는 회원 계정만, 관리자 로그인 API는 관리자 계정만 받는다(#163). 다른 쪽 계정이면 비밀번호가 맞아도
	 * 틀린 것과 똑같이 응답하고 실패 횟수도 센다. 맞았을 때 횟수를 지우면 로봇 확인 요구가 풀리는 것으로 비밀번호가 맞았는지 알 수 있다.
	 * 관리자 로그인 API로 온 실패와, 회원 로그인 API로 관리자 계정을 노린 시도는 감사 로그에 남긴다.
	 */
	private Member authenticate(LoginRequest request, String clientIp, boolean adminApi) {
		String email = ContactFormats.loginEmail(request.email());
		Member member = memberRepository.findByEmail(email).orElse(null);
		boolean adminAccount = member != null && member.getRole().canAccessAdminApi();
		try {
			loginAttemptLimiter.checkAllowed(email, request.captchaToken(), clientIp);

			String passwordHash = member == null ? dummyPasswordHash : member.getPasswordHash();
			boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);

			// 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분해서 알려주지 않고, 둘 다 실패 횟수를 센다.
			if (member == null || member.getStatus() == MemberStatus.WITHDRAWN || !passwordMatches
					|| adminAccount != adminApi) {
				loginAttemptLimiter.recordFailure(email);
				throw new BusinessException(ErrorCode.LOGIN_FAILED);
			}
			loginAttemptLimiter.reset(email);
			// 계정 상태는 본인 확인(비밀번호 일치)이 끝난 뒤에만 알려준다.
			if (member.getStatus() == MemberStatus.SUSPENDED) {
				throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
			}
			return member;
		} catch (BusinessException e) {
			if (adminApi || adminAccount) {
				recordAdminLogin(null, clientIp, email, e.getErrorCode().getStatus().value(),
						adminApi ? ADMIN_LOGIN_FAILED : ADMIN_MEMBER_LOGIN_REJECTED);
			}
			throw e;
		}
	}

	private AuthTokens issueTokens(Member member) {
		String refreshToken = refreshTokenService.issue(member.getId(), member.getAccessTokenVersion());
		return new AuthTokens(accessToken(member), jwtTokenProvider.accessTokenExpiry(), refreshToken,
				refreshTokenService.memberTokenLifetimeSeconds());
	}

	/** 관리자 세션: 30분 동안 재발급하지 않거나 로그인한 지 8시간이 지나면 끝난다(security.admin, #164). */
	private AuthTokens issueAdminTokens(Member admin, LocalDateTime sessionExpiresAt) {
		RefreshTokenService.IssuedToken refreshToken = refreshTokenService.issueAdmin(admin.getId(),
				admin.getAccessTokenVersion(), sessionExpiresAt, adminSecurity.sessionIdleTimeout());
		return new AuthTokens(accessToken(admin), jwtTokenProvider.accessTokenExpiry(), refreshToken.token(),
				refreshToken.expiresIn());
	}

	private String accessToken(Member member) {
		return jwtTokenProvider.createAccessToken(member.getId(), member.getRole().name(),
				member.getAccessTokenVersion());
	}

	private LocalDateTime newAdminSessionExpiry() {
		return LocalDateTime.now().plus(adminSecurity.sessionMaxLifetime());
	}

	/**
	 * 관리자 로그인 1단계 결과. 둘 중 하나만 있다.
	 *
	 * @param tokens       OTP를 요구하지 않아 바로 준 토큰
	 * @param mfaChallenge OTP를 요구할 때 OTP 확인용 1회용 토큰
	 */
	public record AdminLoginResult(AuthTokens tokens, AdminMfaService.Challenge mfaChallenge) {
	}

}
