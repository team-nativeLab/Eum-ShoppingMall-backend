package com.example.shopping.global.config;

import java.time.Clock;
import java.util.Arrays;
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

import com.example.shopping.global.security.AccessTokenValidator;
import com.example.shopping.global.security.JwtProperties;
import com.example.shopping.global.security.JwtTokenProvider;
import com.example.shopping.global.security.RateLimitFilter;
import com.example.shopping.global.security.RequestSizeLimitFilter;
import com.example.shopping.global.security.SecurityErrorHandler;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	/*
	 * 로그아웃도 여기에 둔다. 로그아웃은 쿠키에 담긴 Refresh 토큰을 폐기할 뿐이라 Access 토큰이 필요 없고,
	 * Access 토큰이 만료된 뒤(15분)에 누른 로그아웃이 401로 막히면 Refresh 토큰이 남아 다른 사람이 그 브라우저로
	 * 로그인 상태를 되살릴 수 있다.
	 */
	private static final String[] PUBLIC_AUTH = {
			"/api/v1/auth/signup",
			"/api/v1/auth/login",
			"/api/v1/auth/refresh",
			"/api/v1/auth/logout",
			"/api/v1/admin/auth/login",
	};

	// 로그인 없이 조회 가능한 공개 데이터 (메인, 카테고리, 상품, 공지, FAQ, 회사소개, 앱 설정, 업로드된 이미지)
	private static final String[] PUBLIC_GET = {
			"/files/**",
			"/api/v1/home",
			"/api/v1/app/config",
			"/api/v1/contents/**",
			"/api/v1/categories/**",
			"/api/v1/products/**",
			"/api/v1/notices/**",
			"/api/v1/faqs/**",
	};

	private static final String[] API_DOCS = { "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**" };

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, SecurityErrorHandler errorHandler,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
			StringRedisTemplate redisTemplate,
			@Value("${security.rate-limit.requests-per-minute}") int requestsPerMinute,
			@Value("${security.request.max-body-bytes}") long maxBodyBytes) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(PUBLIC_AUTH).permitAll()
						.requestMatchers("/error").permitAll()
						.requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
						// 견적 문의·1:1 문의 접수는 비회원도 가능하다. 같은 경로의 GET(내 내역 조회)은 로그인이 필요하다.
						.requestMatchers(HttpMethod.POST, "/api/v1/quotes", "/api/v1/support/inquiries").permitAll()
						.requestMatchers(API_DOCS).permitAll()
						.requestMatchers("/actuator/health/**").permitAll()
						// 헬스 체크 외의 운영 엔드포인트는 켜더라도 관리자만 볼 수 있다.
						.requestMatchers("/api/v1/admin/**", "/api/v1/uploads", "/actuator/**").hasRole("ADMIN")
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
				.addFilterBefore(new RateLimitFilter(requestsPerMinute, exceptionResolver, Clock.systemUTC(), redisTemplate),
						BearerTokenAuthenticationFilter.class);
		return http.build();
	}

	/**
	 * 로그인·회원가입·재발급·로그아웃 요청에서는 토큰을 읽지 않는다.
	 * 프론트가 만료된 토큰을 헤더에 붙인 채 로그인하더라도 토큰 오류(401)로 막히지 않게 하기 위함이다.
	 */
	private BearerTokenResolver bearerTokenResolver() {
		PathPatternRequestMatcher.Builder path = PathPatternRequestMatcher.withDefaults();
		RequestMatcher publicAuth = new OrRequestMatcher(Arrays.stream(PUBLIC_AUTH)
				.<RequestMatcher>map(path::matcher)
				.toList());
		DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
		return request -> publicAuth.matches(request) ? null : delegate.resolve(request);
	}

	/** 토큰의 role 클레임(USER, ADMIN)을 ROLE_USER, ROLE_ADMIN 권한으로 바꾼다. */
	private JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(JwtTokenProvider.ROLE_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
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
