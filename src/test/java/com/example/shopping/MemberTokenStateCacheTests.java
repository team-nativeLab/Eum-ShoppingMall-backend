package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.global.security.MemberTokenStateCache;
import com.example.shopping.global.security.MemberTokenStateCache.MemberTokenState;

class MemberTokenStateCacheTests {

	private final MemberRepository memberRepository = mock(MemberRepository.class);
	private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
	@SuppressWarnings("unchecked")
	private final ValueOperations<String, String> values = mock(ValueOperations.class);
	private final Map<String, String> store = new HashMap<>();
	private final MemberTokenStateCache cache = new MemberTokenStateCache(memberRepository, redis, 300);

	MemberTokenStateCacheTests() {
		when(redis.opsForValue()).thenReturn(values);
		when(values.get(anyString())).thenAnswer(invocation -> store.get(invocation.<String>getArgument(0)));
		when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(invocation -> {
			return store.putIfAbsent(invocation.getArgument(0), invocation.getArgument(1)) == null;
		});
		doAnswer(invocation -> store.put(invocation.getArgument(0), invocation.getArgument(1)))
				.when(values).set(anyString(), anyString(), any(Duration.class));
	}

	@Test
	void 처음_한_번만_DB에서_읽고_이후에는_Redis에서_읽는다() {
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1L, MemberStatus.ACTIVE, 3)));

		assertThat(cache.get(1L)).contains(new MemberTokenState(MemberStatus.ACTIVE, 3));
		assertThat(cache.get(1L)).contains(new MemberTokenState(MemberStatus.ACTIVE, 3));
		assertThat(cache.get(1L)).contains(new MemberTokenState(MemberStatus.ACTIVE, 3));

		verify(memberRepository, times(1)).findById(1L);
		verify(values).setIfAbsent("auth:member-token-state:1", "ACTIVE:3", Duration.ofSeconds(300));
	}

	@Test
	void 회원_상태를_바꾸면_새_값으로_덮어써_DB를_다시_읽지_않아도_반영된다() {
		Member member = member(1L, MemberStatus.ACTIVE, 3);
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
		cache.get(1L);

		ReflectionTestUtils.setField(member, "status", MemberStatus.SUSPENDED);
		ReflectionTestUtils.setField(member, "accessTokenVersion", 4L);
		cache.refresh(member);

		assertThat(cache.get(1L)).contains(new MemberTokenState(MemberStatus.SUSPENDED, 4));
		verify(memberRepository, times(1)).findById(1L);
	}

	@Test
	void 트랜잭션_안에서_바꾸면_커밋된_뒤에만_반영한다() {
		store.put("auth:member-token-state:1", "ACTIVE:3");
		TransactionSynchronizationManager.initSynchronization();
		try {
			cache.refresh(member(1L, MemberStatus.WITHDRAWN, 4));
			// 아직 커밋 전이다. 롤백되면 이 값이 그대로 남아야 한다.
			assertThat(store).containsEntry("auth:member-token-state:1", "ACTIVE:3");

			TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
			assertThat(store).containsEntry("auth:member-token-state:1", "WITHDRAWN:4");
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void DB에서_이전_값을_읽는_사이_새_값이_들어왔으면_이전_값으로_덮어쓰지_않는다() {
		// 검증 요청이 DB에서 이전 값을 읽은 직후, 비밀번호 변경이 새 값을 넣은 상황
		when(memberRepository.findById(1L)).thenAnswer(invocation -> {
			cache.refresh(member(1L, MemberStatus.ACTIVE, 4));
			return Optional.of(member(1L, MemberStatus.ACTIVE, 3));
		});

		cache.get(1L);

		assertThat(store).containsEntry("auth:member-token-state:1", "ACTIVE:4");
	}

	@Test
	void Redis가_끊기면_DB에서_읽는다() {
		when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1L, MemberStatus.ACTIVE, 3)));

		assertThat(cache.get(1L)).contains(new MemberTokenState(MemberStatus.ACTIVE, 3));
		verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
	}

	@Test
	void 새_값을_쓰지_못하면_이전_값이_남지_않게_지운다() {
		store.put("auth:member-token-state:1", "ACTIVE:3");
		doAnswer(invocation -> {
			throw new RedisConnectionFailureException("down");
		}).when(values).set(anyString(), anyString(), any(Duration.class));
		when(redis.delete(anyString())).thenAnswer(invocation -> store.remove(invocation.<String>getArgument(0)) != null);

		cache.refresh(member(1L, MemberStatus.SUSPENDED, 3));

		assertThat(store).doesNotContainKey("auth:member-token-state:1");
	}

	@Test
	void 형식이_맞지_않는_값은_무시하고_DB에서_읽는다() {
		store.put("auth:member-token-state:1", "garbage");
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1L, MemberStatus.ACTIVE, 3)));

		assertThat(cache.get(1L)).contains(new MemberTokenState(MemberStatus.ACTIVE, 3));
	}

	@Test
	void 없는_회원은_캐시에_넣지_않는다() {
		when(memberRepository.findById(anyLong())).thenReturn(Optional.empty());

		assertThat(cache.get(9L)).isEmpty();
		verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
	}

	@Test
	void TTL이_0이면_Redis를_쓰지_않고_매번_DB에서_읽는다() {
		MemberTokenStateCache disabled = new MemberTokenStateCache(memberRepository, redis, 0);
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1L, MemberStatus.ACTIVE, 3)));

		disabled.get(1L);
		disabled.get(1L);
		disabled.refresh(member(1L, MemberStatus.SUSPENDED, 3));

		verify(memberRepository, times(2)).findById(1L);
		verify(redis, never()).opsForValue();
	}

	private Member member(Long id, MemberStatus status, long accessTokenVersion) {
		Member member = Member.create("member@example.com", "hash", "홍길동", null);
		ReflectionTestUtils.setField(member, "id", id);
		ReflectionTestUtils.setField(member, "status", status);
		ReflectionTestUtils.setField(member, "accessTokenVersion", accessTokenVersion);
		return member;
	}
}
