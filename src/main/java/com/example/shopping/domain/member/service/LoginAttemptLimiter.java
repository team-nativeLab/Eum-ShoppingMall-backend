package com.example.shopping.domain.member.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.shopping.global.captcha.CaptchaVerifier;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.redis.RedisCircuitBreaker;
import com.example.shopping.global.redis.SharedCounter;
import com.example.shopping.global.security.KeyedHash;
import com.example.shopping.global.security.SecretHashes;

/**
 * 이메일별 로그인 실패 횟수를 센다. IP별 요청 제한(RateLimitFilter)만으로는 IP를 바꿔 가며 한 계정에 비밀번호를 넣어 보는 공격을 막지 못한다.
 * <ul>
 * <li>로봇 확인(app.captcha)을 켜면: 첫 실패부터 10분 안에 정해진 횟수(기본 5번)를 틀린 이메일은 다음 로그인부터 로봇 확인 토큰을 함께 보내야 한다.
 * 막지는 않으므로, 남이 일부러 틀려도 주인은 로봇 확인을 하고 계속 로그인할 수 있다(#141). 자동화된 대입만 느려진다.</li>
 * <li>로봇 확인을 끄면: 정해진 횟수(기본 10번)를 틀리면 그 10분이 끝날 때까지 맞는 비밀번호로도 로그인할 수 없다(429).</li>
 * </ul>
 * 없는 이메일도 똑같이 세어 가입 여부가 드러나지 않게 한다. 로그인에 성공하거나 비밀번호를 재설정하면 센 횟수를 지운다.
 * 횟수는 Redis로 서버끼리 공유하고, Redis 장애 중에는 서버별로 센다(SharedCounter).
 */
@Component
public class LoginAttemptLimiter {

	static final Duration WINDOW = Duration.ofMinutes(10);
	/** 로그인 화면의 Turnstile 위젯에 프론트가 넣는 action 이름. 다른 용도의 위젯에서 받은 토큰은 받지 않는다. */
	public static final String CAPTCHA_ACTION = "login";
	private static final String KEY_PREFIX = "auth:login-failures:";
	private static final int MAX_LOCAL_EMAILS = 10_000;

	private final SharedCounter failures;
	private final KeyedHash keyHashes;
	private final CaptchaVerifier captchaVerifier;
	private final int captchaAfterFailures;
	private final int maxFailures;

	@Autowired
	public LoginAttemptLimiter(StringRedisTemplate redis, RedisCircuitBreaker redisCircuitBreaker,
			SecretHashes secretHashes, CaptchaVerifier captchaVerifier,
			@Value("${security.login-lock.captcha-after-failures:5}") int captchaAfterFailures,
			@Value("${security.login-lock.max-failures:10}") int maxFailures) {
		this(redis, redisCircuitBreaker, secretHashes.forPurpose(SecretHashes.LOGIN_LOCK_KEY), captchaVerifier,
				captchaAfterFailures, maxFailures, Clock.systemUTC());
	}

	LoginAttemptLimiter(StringRedisTemplate redis, RedisCircuitBreaker redisCircuitBreaker, KeyedHash keyHashes,
			CaptchaVerifier captchaVerifier, int captchaAfterFailures, int maxFailures, Clock clock) {
		this.failures = new SharedCounter("login-failure", redis, redisCircuitBreaker, clock, MAX_LOCAL_EMAILS);
		this.keyHashes = keyHashes;
		this.captchaVerifier = captchaVerifier;
		this.captchaAfterFailures = captchaAfterFailures;
		this.maxFailures = maxFailures;
	}

	/**
	 * 비밀번호를 확인하기 전에 부른다. 로봇 확인을 켰으면 많이 틀린 이메일에 토큰을 요구하고(없으면 CAPTCHA_REQUIRED,
	 * 실패하면 CAPTCHA_INVALID), 껐으면 막힌 이메일에 LOGIN_LOCKED(429)를 준다. 이때는 비밀번호를 비교하지 않으므로 횟수도 늘지 않는다.
	 * 로봇 확인 서비스를 부르므로 DB 트랜잭션 밖에서 부른다.
	 */
	public void checkAllowed(String email, String captchaToken, String remoteIp) {
		long count = failures.get(key(email));
		if (!captchaVerifier.isEnabled()) {
			if (count >= maxFailures) {
				throw new BusinessException(ErrorCode.LOGIN_LOCKED);
			}
			return;
		}
		if (count < captchaAfterFailures) {
			return;
		}
		if (!StringUtils.hasText(captchaToken)) {
			throw new BusinessException(ErrorCode.CAPTCHA_REQUIRED);
		}
		if (!captchaVerifier.verify(captchaToken, CAPTCHA_ACTION, remoteIp)) {
			throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
		}
	}

	public void recordFailure(String email) {
		failures.increment(key(email), WINDOW);
	}

	public void reset(String email) {
		failures.reset(key(email));
	}

	/** Redis 키에 이메일을 그대로 남기지 않는다. 서버 키로 해시해 Redis를 볼 수 있어도 이메일 목록을 넣어 보며 되돌릴 수 없다(#136). */
	String key(String email) {
		return KEY_PREFIX + keyHashes.hash(email);
	}
}
