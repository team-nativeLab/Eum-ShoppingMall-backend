package com.example.shopping.global.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 보안 필터 단계에서 나는 401·403도 컨트롤러 예외와 같은 응답 포맷으로 내려준다.
 * 응답 작성은 GlobalExceptionHandler에 맡긴다.
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private static final String CART_PATH = "/api/v1/cart";

	private final HandlerExceptionResolver resolver;

	public SecurityErrorHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) {
		resolver.resolveException(request, response, null, new BusinessException(errorCodeFor(request, authException)));
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) {
		resolver.resolveException(request, response, null, new BusinessException(ErrorCode.FORBIDDEN));
	}

	/**
	 * 프론트는 이 코드로 다음 행동을 정한다.
	 * TOKEN_EXPIRED면 재발급을 시도하고, LOGIN_REQUIRED면 로그인·회원가입을 안내하고, 그 밖의 401은 로그인 화면으로 보낸다.
	 */
	private ErrorCode errorCodeFor(HttpServletRequest request, AuthenticationException e) {
		if (isExpiredToken(e)) {
			return ErrorCode.TOKEN_EXPIRED;
		}
		if (hasValidationError(e, AccessTokenValidator.STATE_UNAVAILABLE.getErrorCode())) {
			return ErrorCode.SERVICE_UNAVAILABLE;
		}
		// 토큰 없이(비회원으로) 장바구니를 쓰려고 한 경우. 잘못되거나 만료된 토큰을 보낸 경우는 해당하지 않는다.
		if (e instanceof InsufficientAuthenticationException && isCartRequest(request)) {
			return ErrorCode.LOGIN_REQUIRED;
		}
		return ErrorCode.UNAUTHORIZED;
	}

	private boolean isCartRequest(HttpServletRequest request) {
		String uri = request.getRequestURI();
		return uri.equals(CART_PATH) || uri.startsWith(CART_PATH + "/");
	}

	private boolean hasValidationError(AuthenticationException e, String errorCode) {
		return e.getCause() instanceof JwtValidationException validation
				&& validation.getErrors().stream().anyMatch(error -> errorCode.equals(error.getErrorCode()));
	}

	private boolean isExpiredToken(AuthenticationException e) {
		return e.getCause() instanceof JwtValidationException validation
				&& validation.getErrors().stream()
						.anyMatch(error -> error.getDescription() != null && error.getDescription().contains("expired"));
	}
}
