package com.example.shopping.domain.member.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.member.controller.AuthTokenResponder.Session;
import com.example.shopping.domain.member.dto.request.LoginRequest;
import com.example.shopping.domain.member.dto.response.LoginResponse;
import com.example.shopping.domain.member.service.AuthService;
import com.example.shopping.global.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 세션은 회원 세션과 따로 둔다(#163). Refresh 쿠키(admin_refresh_token)는 이 경로로만 가고,
 * 재발급·로그아웃은 관리자 화면 주소(CORS_ADMIN_ORIGINS)에서 온 요청만 받는다(RefreshCookieOriginFilter).
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

	private final AuthService authService;
	private final AuthTokenResponder responder;

	/** ADM-001 관리자 로그인. 권한이 ADMIN인 계정만 토큰을 받는다. */
	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest) {
		return responder.withTokens(Session.ADMIN, authService.adminLogin(request, httpRequest.getRemoteAddr()));
	}

	/** 관리자 Refresh 쿠키로 Access 토큰을 다시 발급한다. Refresh 토큰도 새것으로 교체된다. */
	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<LoginResponse>> refresh(
			@CookieValue(name = AuthTokenResponder.ADMIN_REFRESH_COOKIE, required = false) String refreshToken) {
		return responder.withTokens(Session.ADMIN, authService.adminRefresh(refreshToken));
	}

	/** Authorization 헤더의 Access 토큰과 관리자 Refresh 쿠키를 폐기한다. */
	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(
			@CookieValue(name = AuthTokenResponder.ADMIN_REFRESH_COOKIE, required = false) String refreshToken,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
		authService.logout(refreshToken, authorizationHeader);
		return responder.withoutTokens(Session.ADMIN);
	}
}
