package com.example.shopping.domain.member.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.member.controller.AuthTokenResponder.Session;
import com.example.shopping.domain.member.dto.request.EmailVerificationConfirmRequest;
import com.example.shopping.domain.member.dto.request.EmailVerificationSendRequest;
import com.example.shopping.domain.member.dto.request.LoginRequest;
import com.example.shopping.domain.member.dto.request.PasswordResetConfirmRequest;
import com.example.shopping.domain.member.dto.response.EmailVerificationConfirmResponse;
import com.example.shopping.domain.member.dto.response.EmailVerificationSendResponse;
import com.example.shopping.domain.member.dto.response.LoginResponse;
import com.example.shopping.domain.member.dto.request.SignupRequest;
import com.example.shopping.domain.member.dto.response.SignupResponse;
import com.example.shopping.domain.member.service.AuthService;
import com.example.shopping.domain.member.service.EmailVerificationService;
import com.example.shopping.domain.member.service.PasswordResetService;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "인증")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final AuthTokenResponder responder;
	private final EmailVerificationService emailVerificationService;
	private final PasswordResetService passwordResetService;

	/** WEB-019 회원가입 */
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ApiResponse.ok(authService.signup(request));
	}

	/**
	 * 회원가입 이메일 인증번호를 보낸다. 이미 가입된 이메일이면 인증번호 대신 안내 메일을 보내고 응답은 같다. 1분 안에 다시 요청하면 429.
	 * 로봇 확인을 켰고 하루에 3통을 넘게 요청하면 captchaToken(action=send-code)이 필요하다(#166).
	 */
	@PostMapping("/email-verifications")
	public ApiResponse<EmailVerificationSendResponse> sendEmailVerification(
			@Valid @RequestBody EmailVerificationSendRequest request, HttpServletRequest httpRequest) {
		return ApiResponse.ok(emailVerificationService.sendSignupCode(request.email(), request.captchaToken(),
				httpRequest.getRemoteAddr()));
	}

	/**
	 * 인증번호를 확인하고 회원가입 요청에 넣을 확인 토큰을 준다.
	 * 3번 틀리면 다음부터 로봇 확인 토큰(captchaToken)이 필요하고(CAPTCHA_REQUIRED), 5번 틀리면 1시간 동안 막힌다(429).
	 */
	@PostMapping("/email-verifications/confirm")
	public ApiResponse<EmailVerificationConfirmResponse> confirmEmailVerification(
			@Valid @RequestBody EmailVerificationConfirmRequest request, HttpServletRequest httpRequest) {
		return ApiResponse.ok(emailVerificationService.confirmSignupCode(request.email(), request.code(),
				request.captchaToken(), httpRequest.getRemoteAddr()));
	}

	/**
	 * 비밀번호 재설정 인증번호를 보낸다. 가입 여부가 드러나지 않도록 가입되지 않은 이메일이어도 똑같이 성공으로 응답한다.
	 * 로봇 확인을 켰고 하루에 3통을 넘게 요청하면 captchaToken(action=send-code)이 필요하다(#166).
	 */
	@PostMapping("/password-reset")
	public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody EmailVerificationSendRequest request,
			HttpServletRequest httpRequest) {
		passwordResetService.requestReset(request.email(), request.captchaToken(), httpRequest.getRemoteAddr());
		return ApiResponse.ok(null);
	}

	/** 인증번호를 확인하고 비밀번호를 바꾼다. 다른 기기의 로그인은 모두 끊긴다. 틀렸을 때의 제한은 가입 인증번호와 같다. */
	@PostMapping("/password-reset/confirm")
	public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request,
			HttpServletRequest httpRequest) {
		passwordResetService.resetPassword(request.email(), request.code(), request.newPassword(),
				request.captchaToken(), httpRequest.getRemoteAddr());
		return ApiResponse.ok(null);
	}

	/**
	 * WEB-018 로그인. Access 토큰은 응답 본문으로, Refresh 토큰은 HttpOnly 쿠키로 내려준다.
	 * 관리자 계정은 비밀번호가 맞아도 실패로 응답한다. 관리자는 관리자 로그인(ADM-001)만 쓴다(#163).
	 */
	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest) {
		return responder.withTokens(Session.MEMBER, authService.login(request, httpRequest.getRemoteAddr()));
	}

	/** Refresh 쿠키로 Access 토큰을 다시 발급한다. Refresh 토큰도 새것으로 교체된다. 관리자 토큰은 발급하지 않는다. */
	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<LoginResponse>> refresh(
			@CookieValue(name = AuthTokenResponder.REFRESH_COOKIE, required = false) String refreshToken) {
		return responder.withTokens(Session.MEMBER, authService.refresh(refreshToken));
	}

	/** Authorization 헤더의 Access 토큰과 Refresh 쿠키를 폐기한다. */
	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(
			@CookieValue(name = AuthTokenResponder.REFRESH_COOKIE, required = false) String refreshToken,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
		authService.logout(refreshToken, authorizationHeader);
		return responder.withoutTokens(Session.MEMBER);
	}
}
