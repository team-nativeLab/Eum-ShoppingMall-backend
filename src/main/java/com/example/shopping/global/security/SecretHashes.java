package com.example.shopping.global.security;

import org.springframework.stereotype.Component;

/**
 * 저장하는 해시의 원칙(#136).
 * <ul>
 * <li>서버가 비교하거나 다시 찾는 값(인증번호, 가입 확인 토큰, Refresh 토큰, 가상계좌 웹훅 비밀값, Redis 키에 넣는 이메일·IP)은
 * 여기서 뽑은 용도별 키로 HMAC-SHA256 해시한다. 같은 값이면 늘 같은 해시가 나와야 다시 찾을 수 있으므로 값마다 다른 솔트는 넣지 않는다.</li>
 * <li>비밀번호처럼 사용자가 정해 다른 사이트에도 쓸 수 있는 값은 이것 대신 값마다 솔트를 넣는 느린 해시(BCrypt)로 저장한다.</li>
 * </ul>
 * 키는 JWT 서명 키(JWT_SECRET)에서 용도별로 뽑는다. JWT_SECRET을 바꾸면 이 해시도 모두 바뀌므로,
 * 센 횟수는 처음부터 다시 세고, 진행 중인 인증번호·가입 확인 토큰은 다시 받아야 하며, 모든 회원이 다시 로그인해야 한다.
 */
@Component
public class SecretHashes {

	public static final String EMAIL_VERIFICATION_CODE = "email-verification-code";
	public static final String EMAIL_VERIFICATION_TOKEN = "email-verification-token";
	public static final String REFRESH_TOKEN = "refresh-token";
	public static final String PAYMENT_WEBHOOK_SECRET = "payment-webhook-secret";
	public static final String LOGIN_LOCK_KEY = "login-lock-key";
	public static final String VERIFICATION_LOCK_KEY = "verification-lock-key";
	public static final String RATE_LIMIT_KEY = "rate-limit-key";

	private final String secret;

	public SecretHashes(JwtProperties jwtProperties) {
		this.secret = jwtProperties.secret();
	}

	/** 용도별 키로 해시하는 함수. 용도 이름은 위 상수를 쓴다. */
	public KeyedHash forPurpose(String purpose) {
		return KeyedHash.derive(secret, purpose);
	}
}
