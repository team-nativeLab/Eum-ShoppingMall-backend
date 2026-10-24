package com.example.shopping.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.global.redis.RedisCircuitBreaker;
import com.example.shopping.global.security.AccessTokenStateStore.AccessTokenStateUnavailableException;
import com.example.shopping.global.security.MemberTokenStateCache.MemberTokenState;

/** 토큰 차단 확인용 Redis에 장애가 났을 때의 동작 (#70) */
class AccessTokenRedisOutageTest {

	private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
	private final RedisAccessTokenStateStore store = new RedisAccessTokenStateStore(redis, new RedisCircuitBreaker());

	@Test
	void Redis가_끊기면_차단_여부를_확인할_수_없다고_알린다() {
		when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("down"));

		assertThatThrownBy(() -> store.isRevoked("token-1")).isInstanceOf(AccessTokenStateUnavailableException.class);
	}

	@Test
	void Redis가_끊겨도_로그아웃은_실패하지_않고_이_서버는_그_토큰을_계속_거절한다() {
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
				.when(values).set(anyString(), anyString(), any(Duration.class));
		when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("down"));

		assertThatCode(() -> store.revoke("token-1", Duration.ofMinutes(15))).doesNotThrowAnyException();

		assertThat(store.isRevoked("token-1")).isTrue();
		assertThatThrownBy(() -> store.isRevoked("token-2")).isInstanceOf(AccessTokenStateUnavailableException.class);
	}

	@Test
	void 차단_여부를_확인할_수_없으면_일반_회원은_토큰_버전만으로_통과하고_관리자는_503용_오류를_받는다() {
		MemberTokenStateCache memberStates = mock(MemberTokenStateCache.class);
		when(memberStates.get(1L)).thenReturn(Optional.of(new MemberTokenState(MemberStatus.ACTIVE, 2)));
		AccessTokenStateStore unavailable = mock(AccessTokenStateStore.class);
		when(unavailable.isRevoked(anyString())).thenThrow(new AccessTokenStateUnavailableException(null));
		AccessTokenValidator validator = new AccessTokenValidator(memberStates, unavailable);

		assertThat(validator.validate(token("USER", 2)).hasErrors()).isFalse();
		// 비밀번호 변경 등으로 버전이 바뀐 토큰은 Redis 없이도 거절한다.
		assertThat(validator.validate(token("USER", 1)).getErrors())
				.extracting(error -> error.getErrorCode()).containsExactly("invalid_token");

		OAuth2TokenValidatorResult admin = validator.validate(token("ADMIN", 2));
		assertThat(admin.getErrors()).containsExactly(AccessTokenValidator.STATE_UNAVAILABLE);
		assertThat(validator.validate(token("SUPER_ADMIN", 2)).getErrors())
				.containsExactly(AccessTokenValidator.STATE_UNAVAILABLE);
	}

	private Jwt token(String role, long version) {
		return Jwt.withTokenValue("token")
				.header("alg", "HS256")
				.subject("1")
				.jti("token-id")
				.claim(JwtTokenProvider.ROLE_CLAIM, role)
				.claim(JwtTokenProvider.VERSION_CLAIM, version)
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(900))
				.build();
	}
}
