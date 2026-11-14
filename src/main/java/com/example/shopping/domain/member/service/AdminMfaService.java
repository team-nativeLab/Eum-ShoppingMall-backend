package com.example.shopping.domain.member.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.OptionalLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.member.dto.response.AdminMfaSetupResponse;
import com.example.shopping.domain.member.entity.AdminLoginChallenge;
import com.example.shopping.domain.member.entity.AdminMfa;
import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberRole;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.domain.member.repository.AdminLoginChallengeRepository;
import com.example.shopping.domain.member.repository.AdminMfaRepository;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.monitoring.ManualCheckAlerts;
import com.example.shopping.global.redis.RedisCircuitBreaker;
import com.example.shopping.global.redis.SharedCounter;
import com.example.shopping.global.security.AdminSecurityProperties;
import com.example.shopping.global.security.KeyedHash;
import com.example.shopping.global.security.SecretCipher;
import com.example.shopping.global.security.SecretHashes;
import com.example.shopping.global.security.Totp;

import lombok.extern.slf4j.Slf4j;

/**
 * 관리자 OTP(TOTP) 2단계 인증(#164). 비밀번호를 확인한 관리자에게 1회용 토큰(mfaToken)을 주고, 그 토큰과 OTP 앱의 코드를 받아야
 * 로그인을 마친다. OTP를 등록하지 않은 관리자는 같은 토큰으로 등록부터 한다.
 * <ul>
 * <li>토큰은 5분 동안 쓰고, 5번 틀리면 지운다. 다시 비밀번호부터 넣어야 한다.</li>
 * <li>한 관리자가 15분 안에 10번 틀리면 그동안 코드를 받지 않고 담당자에게 알린다. 비밀번호가 샌 것이기 때문이다.
 * 비밀번호를 다시 넣어 토큰을 새로 받아도 이 횟수는 지워지지 않는다.</li>
 * <li>한 번 받은 코드는 다시 받지 않는다. 같은 30초 칸이나 그 이전 칸의 코드를 거절한다.</li>
 * </ul>
 */
@Slf4j
@Service
public class AdminMfaService {

	static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
	static final int MAX_ATTEMPTS_PER_CHALLENGE = 5;
	static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
	static final int MAX_FAILURES = 10;
	private static final String FAILURE_KEY_PREFIX = "auth:admin-mfa-failures:";
	private static final int MAX_LOCAL_MEMBERS = 1_000;

	private final AdminMfaRepository mfaRepository;
	private final AdminLoginChallengeRepository challengeRepository;
	private final MemberRepository memberRepository;
	private final RefreshTokenService refreshTokenService;
	private final AdminSecurityProperties properties;
	private final ManualCheckAlerts alerts;
	private final KeyedHash challengeHashes;
	private final KeyedHash failureKeys;
	private final SecretCipher secretCipher;
	private final SharedCounter failures;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	@Autowired
	public AdminMfaService(AdminMfaRepository mfaRepository, AdminLoginChallengeRepository challengeRepository,
			MemberRepository memberRepository, RefreshTokenService refreshTokenService,
			AdminSecurityProperties properties, ManualCheckAlerts alerts, SecretHashes secretHashes,
			StringRedisTemplate redis, RedisCircuitBreaker redisCircuitBreaker) {
		this(mfaRepository, challengeRepository, memberRepository, refreshTokenService, properties, alerts,
				secretHashes, redis, redisCircuitBreaker, Clock.systemDefaultZone());
	}

	AdminMfaService(AdminMfaRepository mfaRepository, AdminLoginChallengeRepository challengeRepository,
			MemberRepository memberRepository, RefreshTokenService refreshTokenService,
			AdminSecurityProperties properties, ManualCheckAlerts alerts, SecretHashes secretHashes,
			StringRedisTemplate redis, RedisCircuitBreaker redisCircuitBreaker, Clock clock) {
		this.mfaRepository = mfaRepository;
		this.challengeRepository = challengeRepository;
		this.memberRepository = memberRepository;
		this.refreshTokenService = refreshTokenService;
		this.properties = properties;
		this.alerts = alerts;
		this.challengeHashes = secretHashes.forPurpose(SecretHashes.ADMIN_MFA_CHALLENGE);
		this.failureKeys = secretHashes.forPurpose(SecretHashes.ADMIN_MFA_FAILURE_KEY);
		this.secretCipher = secretHashes.cipherFor(SecretHashes.ADMIN_MFA_SECRET_ENCRYPTION);
		this.failures = new SharedCounter("admin-mfa-failure", redis, redisCircuitBreaker, clock, MAX_LOCAL_MEMBERS);
		this.clock = clock;
	}

	/** 비밀번호를 확인한 뒤 OTP를 요구할지. 설정으로 껐어도 OTP를 등록한 관리자에게는 요구한다. */
	@Transactional(readOnly = true)
	public boolean isRequiredFor(Member admin) {
		return properties.mfaRequired() || mfaRepository.findById(admin.getId()).map(AdminMfa::isEnabled).orElse(false);
	}

	/** 비밀번호를 확인한 관리자에게 OTP 확인용 1회용 토큰을 준다. 그 관리자의 이전 토큰은 지운다. */
	@Transactional
	public Challenge start(Member admin) {
		challengeRepository.deleteAllByMemberId(admin.getId());
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		challengeRepository.save(AdminLoginChallenge.create(admin.getId(), challengeHashes.hash(token),
				admin.getAccessTokenVersion(), now().plus(CHALLENGE_TTL)));
		boolean setupRequired = mfaRepository.findById(admin.getId()).map(mfa -> !mfa.isEnabled()).orElse(true);
		return new Challenge(token, CHALLENGE_TTL.toSeconds(), setupRequired);
	}

	/**
	 * OTP 앱에 등록할 새 비밀 키를 만든다. 등록을 마치기 전에만 받을 수 있고, 다시 부르면 새 키로 바뀐다.
	 * 이미 등록했으면 409. 기기를 바꾸려면 최고 관리자가 초기화한다(reset).
	 */
	@Transactional
	public AdminMfaSetupResponse setup(String mfaToken) {
		Member admin = verifiedAdmin(activeChallenge(mfaToken));
		AdminMfa mfa = mfaRepository.findById(admin.getId()).orElse(null);
		if (mfa != null && mfa.isEnabled()) {
			throw new BusinessException(ErrorCode.ADMIN_MFA_ALREADY_ENABLED);
		}
		byte[] secret = new byte[Totp.SECRET_BYTES];
		random.nextBytes(secret);
		String ciphertext = secretCipher.encrypt(secret, boundTo(admin.getId()));
		if (mfa == null) {
			mfaRepository.save(AdminMfa.pending(admin.getId(), ciphertext));
		} else {
			mfa.replacePendingSecret(ciphertext);
		}
		return new AdminMfaSetupResponse(Totp.base32(secret),
				Totp.otpauthUri(properties.mfaIssuer(), admin.getEmail(), secret));
	}

	/** 토큰 주인. 토큰이 없거나 만료됐거나, 주인의 비밀번호·권한·상태가 바뀌었으면 ADMIN_MFA_TOKEN_INVALID */
	public Member challengedAdmin(String mfaToken) {
		return verifiedAdmin(activeChallenge(mfaToken));
	}

	/**
	 * OTP 코드를 확인한다. 맞으면 토큰을 지우고, 처음 넣은 코드면 등록을 마친다. 틀리면 ADMIN_MFA_CODE_INVALID,
	 * 많이 틀린 관리자는 ADMIN_MFA_LOCKED, 등록을 시작하지 않았으면 ADMIN_MFA_SETUP_REQUIRED.
	 *
	 * @return 이번 코드로 OTP 등록을 마쳤는지
	 */
	public boolean verify(String mfaToken, String code) {
		AdminLoginChallenge challenge = activeChallenge(mfaToken);
		Long memberId = verifiedAdmin(challenge).getId();
		String failureKey = FAILURE_KEY_PREFIX + failureKeys.hash(String.valueOf(memberId));
		if (failures.get(failureKey) >= MAX_FAILURES) {
			challengeRepository.deleteChallenge(challenge.getId());
			throw new BusinessException(ErrorCode.ADMIN_MFA_LOCKED);
		}
		AdminMfa mfa = mfaRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_MFA_SETUP_REQUIRED));
		byte[] secret = decryptOrForget(mfa);

		OptionalLong step = Totp.matchingStep(secret, code, clock.instant());
		if (step.isEmpty() || mfaRepository.markUsed(memberId, step.getAsLong(), mfa.getSecretCiphertext(), now()) == 0) {
			recordFailure(challenge, memberId, failureKey);
			throw new BusinessException(ErrorCode.ADMIN_MFA_CODE_INVALID);
		}
		failures.reset(failureKey);
		challengeRepository.deleteChallenge(challenge.getId());
		return !mfa.isEnabled();
	}

	/** 최고 관리자가 관리자의 OTP를 지운다(기기 분실·교체). 그 관리자는 다시 로그인해 새로 등록해야 한다. */
	@Transactional
	public void reset(Long actorId, Long targetId) {
		Member actor = memberRepository.findById(actorId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (actor.getRole() != MemberRole.SUPER_ADMIN) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		if (!memberRepository.existsById(targetId)) {
			throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
		}
		mfaRepository.deleteByMemberId(targetId);
		challengeRepository.deleteAllByMemberId(targetId);
		refreshTokenService.revokeAll(targetId);
	}

	public int purgeExpiredChallenges() {
		return challengeRepository.deleteExpired(now());
	}

	private AdminLoginChallenge activeChallenge(String mfaToken) {
		if (mfaToken == null || mfaToken.isBlank()) {
			throw new BusinessException(ErrorCode.ADMIN_MFA_TOKEN_INVALID);
		}
		AdminLoginChallenge challenge = challengeRepository.findByTokenHash(challengeHashes.hash(mfaToken))
				.orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_MFA_TOKEN_INVALID));
		if (!challenge.getExpiresAt().isAfter(now())) {
			challengeRepository.deleteChallenge(challenge.getId());
			throw new BusinessException(ErrorCode.ADMIN_MFA_TOKEN_INVALID);
		}
		return challenge;
	}

	private Member verifiedAdmin(AdminLoginChallenge challenge) {
		return memberRepository.findById(challenge.getMemberId())
				.filter(member -> member.getStatus() == MemberStatus.ACTIVE)
				.filter(member -> member.getRole().canAccessAdminApi())
				.filter(member -> member.getAccessTokenVersion() == challenge.getTokenVersion())
				.orElseThrow(() -> {
					challengeRepository.deleteChallenge(challenge.getId());
					return new BusinessException(ErrorCode.ADMIN_MFA_TOKEN_INVALID);
				});
	}

	/**
	 * JWT_SECRET을 바꾸면 전에 암호화한 비밀 키를 풀 수 없다. 이때는 그 키를 지우고 다시 등록하게 한다.
	 * 비밀번호를 확인한 사람만 여기까지 오므로, 처음 등록할 때와 같은 수준으로 보호된다.
	 */
	private byte[] decryptOrForget(AdminMfa mfa) {
		try {
			return secretCipher.decrypt(mfa.getSecretCiphertext(), boundTo(mfa.getMemberId()));
		} catch (IllegalStateException e) {
			log.warn("관리자 {}의 OTP 비밀 키를 풀 수 없어 지웁니다. JWT_SECRET을 바꿨다면 관리자가 OTP를 다시 등록해야 합니다.",
					mfa.getMemberId());
			mfaRepository.deleteByMemberId(mfa.getMemberId());
			throw new BusinessException(ErrorCode.ADMIN_MFA_SETUP_REQUIRED);
		}
	}

	private void recordFailure(AdminLoginChallenge challenge, Long memberId, String failureKey) {
		long count = failures.increment(failureKey, FAILURE_WINDOW);
		if (challenge.getFailedAttempts() + 1 >= MAX_ATTEMPTS_PER_CHALLENGE || count >= MAX_FAILURES) {
			challengeRepository.deleteChallenge(challenge.getId());
		} else {
			challengeRepository.recordFailure(challenge.getId());
		}
		if (count == MAX_FAILURES) {
			alerts.raise("admin_mfa_locked", "관리자 {}가 15분 안에 OTP를 {}번 틀려 잠시 막았습니다. 비밀번호가 샜을 수 있으니 "
					+ "감사 로그를 확인하고 비밀번호를 바꿔 주세요.", memberId, count);
		}
	}

	private static String boundTo(Long memberId) {
		return "admin-mfa:" + memberId;
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	/**
	 * @param mfaToken      OTP를 넣을 때 함께 보낼 1회용 토큰
	 * @param expiresIn     토큰 유효 시간(초)
	 * @param setupRequired OTP를 아직 등록하지 않았는지
	 */
	public record Challenge(String mfaToken, long expiresIn, boolean setupRequired) {
	}
}
