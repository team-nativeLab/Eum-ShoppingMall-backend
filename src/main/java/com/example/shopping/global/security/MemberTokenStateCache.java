package com.example.shopping.global.security;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.global.redis.RedisCircuitBreaker;

/**
 * Access 토큰 검증에 쓰는 회원 상태(계정 상태, 토큰 버전)를 공유 Redis에 둔다.
 * 로그인한 요청마다 회원을 DB에서 다시 읽지 않도록 하고, 비밀번호 변경·탈퇴·정지처럼 값이 바뀌면 커밋 직후 새 값으로 덮어쓴다.
 * Redis가 끊기면 DB에서 읽는다. DB를 직접 고친 경우에는 TTL(기본 5분)이 지나야 반영된다.
 */
@Component
public class MemberTokenStateCache {

	private static final Logger log = LoggerFactory.getLogger(MemberTokenStateCache.class);
	private static final String KEY_PREFIX = "auth:member-token-state:";

	private final MemberRepository memberRepository;
	private final StringRedisTemplate redis;
	private final Duration ttl;
	private final RedisCircuitBreaker redisCircuitBreaker;

	public MemberTokenStateCache(MemberRepository memberRepository, StringRedisTemplate redis, long ttlSeconds) {
		this(memberRepository, redis, ttlSeconds, new RedisCircuitBreaker());
	}

	@Autowired
	public MemberTokenStateCache(MemberRepository memberRepository, StringRedisTemplate redis,
			@Value("${app.cache.member-token-state-ttl-seconds:300}") long ttlSeconds,
			RedisCircuitBreaker redisCircuitBreaker) {
		this.memberRepository = memberRepository;
		this.redis = redis;
		this.ttl = Duration.ofSeconds(Math.max(0, ttlSeconds));
		this.redisCircuitBreaker = redisCircuitBreaker;
	}

	/** 없는 회원이면 빈 값 */
	public Optional<MemberTokenState> get(Long memberId) {
		if (ttl.isZero()) {
			return load(memberId);
		}
		String key = KEY_PREFIX + memberId;
		try {
			MemberTokenState cached = MemberTokenState.parse(
					redisCircuitBreaker.execute(() -> redis.opsForValue().get(key)));
			if (cached != null) {
				return Optional.of(cached);
			}
		} catch (DataAccessException ex) {
			log.debug("Member token state cache unavailable; reading from the database", ex);
			return load(memberId);
		}

		Optional<MemberTokenState> state = load(memberId);
		state.ifPresent(value -> {
			try {
				// 읽는 사이 값이 바뀌어 refresh가 먼저 새 값을 넣었다면, 방금 읽은 이전 값으로 덮어쓰지 않는다.
				redisCircuitBreaker.execute(() -> redis.opsForValue().setIfAbsent(key, value.format(), ttl));
			} catch (DataAccessException ex) {
				log.debug("Could not write member token state cache", ex);
			}
		});
		return state;
	}

	/** 회원의 계정 상태나 토큰 버전을 바꾼 뒤 호출한다. 트랜잭션 안이면 커밋된 뒤에 반영한다. */
	public void refresh(Member member) {
		if (ttl.isZero()) {
			return;
		}
		String key = KEY_PREFIX + member.getId();
		String value = MemberTokenState.of(member).format();
		Runnable write = () -> {
			try {
				redisCircuitBreaker.run(() -> redis.opsForValue().set(key, value, ttl));
			} catch (DataAccessException ex) {
				log.error("Could not refresh member token state for member {}; it may stay stale for up to {} seconds",
						member.getId(), ttl.toSeconds(), ex);
				deleteQuietly(key);
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					write.run();
				}
			});
		} else {
			write.run();
		}
	}

	private Optional<MemberTokenState> load(Long memberId) {
		return memberRepository.findById(memberId).map(MemberTokenState::of);
	}

	private void deleteQuietly(String key) {
		try {
			redis.delete(key);
		} catch (DataAccessException ignored) {
			// 위에서 이미 기록했다.
		}
	}

	public record MemberTokenState(MemberStatus status, long accessTokenVersion) {

		static MemberTokenState of(Member member) {
			return new MemberTokenState(member.getStatus(), member.getAccessTokenVersion());
		}

		String format() {
			return status.name() + ":" + accessTokenVersion;
		}

		/** 형식이 맞지 않으면 null (캐시에 없는 것으로 보고 DB에서 다시 읽는다) */
		static MemberTokenState parse(String value) {
			if (value == null) {
				return null;
			}
			int separator = value.indexOf(':');
			if (separator < 0) {
				return null;
			}
			try {
				return new MemberTokenState(MemberStatus.valueOf(value.substring(0, separator)),
						Long.parseLong(value.substring(separator + 1)));
			} catch (IllegalArgumentException ex) {
				return null;
			}
		}
	}
}
