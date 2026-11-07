package com.example.shopping.domain.member.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.member.entity.VerificationPurpose;
import com.example.shopping.global.captcha.CaptchaVerifier;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.redis.RedisCircuitBreaker;
import com.example.shopping.global.redis.SharedCounter;
import com.example.shopping.global.security.KeyedHash;
import com.example.shopping.global.security.SecretHashes;

/**
 * 인증번호(회원가입·비밀번호 재설정)를 틀린 횟수를 이메일·용도별로 센다.
 * <ul>
 * <li>로봇 확인(app.captcha)을 켜면: 첫 실패부터 1시간 안에 3번 틀리면 다음 확인부터 로봇 확인(CAPTCHA)을 요구한다.
 * 막지는 않으므로, 남이 일부러 틀려도 주인은 로봇 확인을 하고 계속 인증할 수 있다(#141).</li>
 * <li>로봇 확인을 끄면: 5번 틀리면 그때부터 1시간 동안 그 이메일로 인증번호를 받거나 확인할 수 없다(429).
 * 1시간이 지나면 처음부터 다시 센다.</li>
 * </ul>
 * 인증번호가 없거나 만료된 경우도 틀린 것으로 세어, 가입되지 않은 이메일도 똑같이 로봇 확인과 차단을 받는다.
 * 횟수는 Redis로 서버끼리 공유하고, Redis 장애 중에는 서버별로 센다(SharedCounter).
 */
@Component
public class VerificationAttemptGuard {

	static final Duration FAILURE_WINDOW = Duration.ofHours(1);
	static final Duration LOCK_DURATION = Duration.ofHours(1);
	/** 인증번호 확인 화면의 Turnstile 위젯에 프론트가 넣는 action 이름. 다른 용도의 위젯에서 받은 토큰은 받지 않는다. */
	public static final String CAPTCHA_ACTION = "verify-code";
	private static final String FAILURE_KEY_PREFIX = "auth:verification-failures:";
	private static final String LOCK_KEY_PREFIX = "auth:verification-lock:";
	private static final int MAX_LOCAL_KEYS = 10_000;

	private final SharedCounter failures;
	private final SharedCounter locks;
	private final KeyedHash keyHashes;
	private final CaptchaVerifier captchaVerifier;
	private final int captchaAfterFailures;
	private final int maxFailures;

	@Autowired
	public VerificationAttemptGuard(StringRedisTemplate redis, RedisCircuitBreaker redisCircuitBreaker,
			CaptchaVerifier captchaVerifier, SecretHashes secretHashes,
			@Value("${security.verification-lock.captcha-after-failures:3}") int captchaAfterFailures,
			@Value("${security.verification-lock.max-failures:5}") int maxFailures) {
		this(redis, redisCircuitBreaker, captchaVerifier, secretHashes.forPurpose(SecretHashes.VERIFICATION_LOCK_KEY),
				captchaAfterFailures, maxFailures, Clock.systemUTC());
	}

	VerificationAttemptGuard(StringRedisTemplate redis, RedisCircuitBreaker redisCircuitBreaker,
			CaptchaVerifier captchaVerifier, KeyedHash keyHashes, int captchaAfterFailures, int maxFailures,
			Clock clock) {
		this.failures = new SharedCounter("verification-failure", redis, redisCircuitBreaker, clock, MAX_LOCAL_KEYS);
		this.locks = new SharedCounter("verification-lock", redis, redisCircuitBreaker, clock, MAX_LOCAL_KEYS);
		this.keyHashes = keyHashes;
		this.captchaVerifier = captchaVerifier;
		this.captchaAfterFailures = captchaAfterFailures;
		this.maxFailures = maxFailures;
	}

	/**
	 * 막힌 이메일·용도면 VERIFICATION_LOCKED(429). 인증번호를 보내기 전에도 부른다.
	 * 로봇 확인을 켜면 막지 않는다. 남이 일부러 틀려도 주인은 로봇 확인을 하고 계속 인증할 수 있다(#141).
	 */
	public void checkNotLocked(String email, VerificationPurpose purpose) {
		if (!captchaVerifier.isEnabled() && locks.get(lockKey(email, purpose)) > 0) {
			throw new BusinessException(ErrorCode.VERIFICATION_LOCKED);
		}
	}

	/**
	 * 인증번호를 확인하기 전에 부른다. 막혔으면 429, 로봇 확인이 필요한데 토큰이 없으면 CAPTCHA_REQUIRED(400),
	 * 로봇 확인에 실패하면 CAPTCHA_INVALID(400). 이때는 인증번호를 비교하지 않으므로 틀린 횟수도 늘지 않는다.
	 * 로봇 확인 서비스를 부르므로 DB 트랜잭션 밖에서 부른다.
	 */
	public void checkAttemptAllowed(String email, VerificationPurpose purpose, String captchaToken, String remoteIp) {
		checkNotLocked(email, purpose);
		if (!captchaVerifier.isEnabled() || failures.get(failureKey(email, purpose)) < captchaAfterFailures) {
			return;
		}
		if (!StringUtils.hasText(captchaToken)) {
			throw new BusinessException(ErrorCode.CAPTCHA_REQUIRED);
		}
		if (!captchaVerifier.verify(captchaToken, CAPTCHA_ACTION, remoteIp)) {
			throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
		}
	}

	/** 틀린 횟수를 하나 올린다. 이번에 한도에 닿아 막혔으면 true. 로봇 확인을 켜면 막지 않으므로 늘 false다. */
	public boolean recordFailure(String email, VerificationPurpose purpose) {
		String failureKey = failureKey(email, purpose);
		if (failures.increment(failureKey, FAILURE_WINDOW) < maxFailures || captchaVerifier.isEnabled()) {
			return false;
		}
		locks.increment(lockKey(email, purpose), LOCK_DURATION);
		// 막힌 1시간이 끝나면 처음부터 다시 센다.
		failures.reset(failureKey);
		return true;
	}

	/**
	 * 로봇 확인을 켰는지. 켜면 3번 틀린 뒤부터 확인마다 로봇 확인을 하므로 하루 틀린 횟수 한도도 두지 않는다
	 * (한도에 일부러 닿게 해 남의 인증을 하루 동안 막을 수 있기 때문이다, #141).
	 */
	public boolean isCaptchaProtected() {
		return captchaVerifier.isEnabled();
	}

	/** 인증에 성공하면 센 횟수를 지운다. */
	public void reset(String email, VerificationPurpose purpose) {
		failures.reset(failureKey(email, purpose));
	}

	/** Redis 키에 이메일을 그대로 남기지 않는다. 서버 키로 해시해 Redis를 볼 수 있어도 이메일 목록을 넣어 보며 되돌릴 수 없다(#136). */
	private String failureKey(String email, VerificationPurpose purpose) {
		return FAILURE_KEY_PREFIX + keyHashes.hash(purpose.name(), email);
	}

	private String lockKey(String email, VerificationPurpose purpose) {
		return LOCK_KEY_PREFIX + keyHashes.hash(purpose.name(), email);
	}
}
