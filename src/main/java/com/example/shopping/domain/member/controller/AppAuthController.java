package com.example.shopping.domain.member.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.member.dto.request.AppRefreshRequest;
import com.example.shopping.domain.member.dto.request.LoginRequest;
import com.example.shopping.domain.member.dto.response.AppTokenResponse;
import com.example.shopping.domain.member.service.AuthService;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 앱 인증(#203). 웹 인증(/api/v1/auth)과 달리 Refresh 토큰을 쿠키가 아닌 응답·요청 본문으로 주고받는다.
 * 브라우저 요청(Origin 헤더가 있는 요청)은 받지 않는다(RefreshCookieOriginFilter). 웹 페이지의 스크립트가 본문으로
 * Refresh 토큰을 받지 못하게 하기 위해서다. 회원가입·이메일 인증·비밀번호 재설정은 웹과 같은 /api/v1/auth API를 쓴다.
 */
@Tag(name = "앱 인증")
@RestController
@RequestMapping("/api/v1/app/auth")
@RequiredArgsConstructor
public class AppAuthController {

	private final AuthService authService;

	/**
	 * 앱 로그인. Access·Refresh 토큰을 모두 응답 본문으로 준다(쿠키 없음). 실패 제한·로봇 확인은 웹 로그인과 같고,
	 * 관리자 계정은 비밀번호가 맞아도 실패로 응답한다.
	 */
	@PostMapping("/login")
	public ApiResponse<AppTokenResponse> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest) {
		return ApiResponse.ok(AppTokenResponse.from(authService.login(request, httpRequest.getRemoteAddr())));
	}

	/**
	 * 앱 토큰 재발급. 본문의 Refresh 토큰으로 Access 토큰을 다시 발급하고 Refresh 토큰도 새것으로 바꾼다.
	 * 응답의 Refresh 토큰으로 바꿔 저장해야 하며, 한 번 쓴 토큰을 다시 보내면 401이다.
	 */
	@PostMapping("/refresh")
	public ApiResponse<AppTokenResponse> refresh(@Valid @RequestBody AppRefreshRequest request) {
		return ApiResponse.ok(AppTokenResponse.from(authService.refresh(request.refreshToken())));
	}

	/** 앱 로그아웃. 본문의 Refresh 토큰과 Authorization 헤더의 Access 토큰을 폐기한다. 앱은 저장한 토큰도 지운다. */
	@PostMapping("/logout")
	public ApiResponse<Void> logout(@Valid @RequestBody AppRefreshRequest request,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
		authService.logout(request.refreshToken(), authorizationHeader);
		return ApiResponse.ok(null);
	}
}
