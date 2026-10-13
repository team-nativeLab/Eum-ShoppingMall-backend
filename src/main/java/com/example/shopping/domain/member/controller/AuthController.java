package com.example.shopping.domain.member.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.member.dto.LoginRequest;
import com.example.shopping.domain.member.dto.LoginResponse;
import com.example.shopping.domain.member.dto.SignupRequest;
import com.example.shopping.domain.member.dto.SignupResponse;
import com.example.shopping.domain.member.service.AuthService;
import com.example.shopping.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final AuthTokenResponder responder;

	/** WEB-019 회원가입 */
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ApiResponse.ok(authService.signup(request));
	}

	/** WEB-018 로그인. Access 토큰은 응답 본문으로, Refresh 토큰은 HttpOnly 쿠키로 내려준다. */
	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		return responder.withTokens(authService.login(request));
	}

	/** Refresh 쿠키로 Access 토큰을 다시 발급한다. Refresh 토큰도 새것으로 교체된다. */
	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<LoginResponse>> refresh(
			@CookieValue(name = AuthTokenResponder.REFRESH_COOKIE, required = false) String refreshToken) {
		return responder.withTokens(authService.refresh(refreshToken));
	}

	/** 이 기기의 Refresh 토큰을 폐기하고 쿠키를 지운다. */
	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(
			@CookieValue(name = AuthTokenResponder.REFRESH_COOKIE, required = false) String refreshToken) {
		authService.logout(refreshToken);
		return responder.withoutTokens();
	}
}
