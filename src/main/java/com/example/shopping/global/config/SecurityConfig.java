package com.example.shopping.global.config;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.example.shopping.global.redis.RedisCircuitBreaker;
import com.example.shopping.global.security.AccessTokenValidator;
import com.example.shopping.global.security.ApiPolicy;
import com.example.shopping.global.security.JwtProperties;
import com.example.shopping.global.security.JwtTokenProvider;
import com.example.shopping.global.security.RateLimitFilter;
import com.example.shopping.global.security.RefreshCookieOriginFilter;
import com.example.shopping.global.security.RequestSizeLimitFilter;
import com.example.shopping.global.security.SecurityErrorHandler;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, SecurityErrorHandler errorHandler,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
			@Value("${cors.allowed-origins}") List<String> allowedOrigins,
			StringRedisTemplate redisTemplate, RedisCircuitBreaker redisCircuitBreaker,
			@Value("${security.rate-limit.requests-per-minute}") int requestsPerMinute,
			@Value("${security.rate-limit.public-product-requests-per-minute:300}") int publicProductRequestsPerMinute,
			@Value("${security.request.max-body-bytes}") long maxBodyBytes) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// 경로별 공개 여부는 ApiPolicy 한 곳에 둔다. 표에 없는 API는 로그인한 회원만 부를 수 있다.
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(paths(ApiPolicy.PUBLIC_AUTH)).permitAll()
						.requestMatchers(HttpMethod.GET, paths(ApiPolicy.PUBLIC_GET)).permitAll()
						.requestMatchers(HttpMethod.POST, paths(ApiPolicy.PUBLIC_POST)).permitAll()
						.requestMatchers(paths(ApiPolicy.PUBLIC_OPERATIONS)).permitAll()
						.requestMatchers(paths(ApiPolicy.ADMIN_API)).hasRole("ADMIN")
						.requestMatchers(paths(ApiPolicy.ADMIN_OPERATIONS)).hasRole("ADMIN")
						.anyRequest().authenticated())
				// Authorization: Bearer 헤더의 Access 토큰을 검증해 로그인 회원으로 인증한다.
				.oauth2ResourceServer(oauth2 -> oauth2
						.bearerTokenResolver(bearerTokenResolver())
						.authenticationEntryPoint(errorHandler)
						.accessDeniedHandler(errorHandler)
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
				.exceptionHandling(e -> e
						.authenticationEntryPoint(errorHandler)
						.accessDeniedHandler(errorHandler))
				.addFilterBefore(new RequestSizeLimitFilter(maxBodyBytes, exceptionResolver),
						BearerTokenAuthenticationFilter.class)
				.addFilterBefore(new RateLimitFilter(requestsPerMinute, publicProductRequestsPerMinute,
						exceptionResolver, Clock.systemUTC(), redisTemplate, redisCircuitBreaker),
						BearerTokenAuthenticationFilter.class)
				.addFilterBefore(new RefreshCookieOriginFilter(allowedOrigins, exceptionResolver),
						BearerTokenAuthenticationFilter.class);
		return http.build();
	}

	/**
	 * 로그인·회원가입·재발급·로그아웃 요청에서는 토큰을 읽지 않는다.
	 * 프론트가 만료된 토큰을 헤더에 붙인 채 로그인하더라도 토큰 오류(401)로 막히지 않게 하기 위함이다.
	 */
	private BearerTokenResolver bearerTokenResolver() {
		PathPatternRequestMatcher.Builder path = PathPatternRequestMatcher.withDefaults();
		RequestMatcher publicAuth = new OrRequestMatcher(ApiPolicy.PUBLIC_AUTH.stream()
				.<RequestMatcher>map(path::matcher)
				.toList());
		DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
		return request -> publicAuth.matches(request) ? null : delegate.resolve(request);
	}

	/** 최고 관리자는 관리자 API 전체 권한과 최고 관리자 전용 권한을 모두 받는다. */
	private static String[] paths(List<String> patterns) {
		return patterns.toArray(String[]::new);
	}

	private JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(JwtTokenProvider.ROLE_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			Collection<GrantedAuthority> tokenAuthorities = authorities.convert(jwt);
			List<GrantedAuthority> result = tokenAuthorities == null
					? new ArrayList<>() : new ArrayList<>(tokenAuthorities);
			if ("SUPER_ADMIN".equals(jwt.getClaimAsString(JwtTokenProvider.ROLE_CLAIM))) {
				result.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
			}
			return result;
		});
		return converter;
	}

	@Bean
	JwtEncoder jwtEncoder(JwtProperties properties) {
		return NimbusJwtEncoder.withSecretKey(properties.secretKey()).algorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties, AccessTokenValidator accessTokenValidator) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.secretKey())
				.macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefault(), accessTokenValidator));
		return decoder;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${cors.allowed-origins}") List<String> allowedOrigins) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(allowedOrigins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
