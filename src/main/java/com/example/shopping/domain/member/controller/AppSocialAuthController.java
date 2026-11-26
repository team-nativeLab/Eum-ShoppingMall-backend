package com.example.shopping.domain.member.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.member.dto.request.AppSocialLoginRequest;
import com.example.shopping.domain.member.dto.request.SocialSignupRequest;
import com.example.shopping.domain.member.dto.response.AppSocialLoginResponse;
import com.example.shopping.domain.member.dto.response.AppTokenResponse;
import com.example.shopping.domain.member.entity.SocialProvider;
import com.example.shopping.domain.member.service.SocialAuthService;
import com.example.shopping.domain.member.service.SocialLoginResult;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 앱 카카오·구글 로그인(#225). 앱 SDK가 받은 토큰을 보내면 서버가 카카오·구글에 확인하고, 앱 로그인처럼 토큰을 모두 본문으로 준다.
 * 브라우저 요청(Origin 헤더가 있는 요청)은 받지 않는다(앱 인증 API와 같다).
 */
@Tag(name = "앱 인증")
@RestController
@RequestMapping("/api/v1/app/auth/social")
@RequiredArgsConstructor
public class AppSocialAuthController {

	private final SocialAuthService socialAuthService;

	/**
	 * 카카오(kakao)는 카카오 SDK의 액세스 토큰, 구글(google)은 Google Sign-In의 ID 토큰을 보낸다.
	 * 다른 앱용으로 발급된 토큰은 받지 않는다. 처음 온 사람이면 signupRequired와 가입 토큰을 준다.
	 */
	@PostMapping("/{provider}/login")
	public ApiResponse<AppSocialLoginResponse> login(@PathVariable String provider,
			@Valid @RequestBody AppSocialLoginRequest request, HttpServletRequest httpRequest) {
		SocialLoginResult result = socialAuthService.loginWithAppToken(SocialProvider.fromPath(provider),
				request.token(), httpRequest.getRemoteAddr());
		return ApiResponse.ok(result.signupRequired() ? AppSocialLoginResponse.signupRequired(result.signup())
				: AppSocialLoginResponse.loggedIn(result.tokens()));
	}

	/** 카카오·구글로 처음 온 사람의 가입. 약관·개인정보 동의를 받아 가입하고 바로 로그인한다(토큰은 본문). */
	@PostMapping("/signup")
	public ApiResponse<AppTokenResponse> signup(@Valid @RequestBody SocialSignupRequest request,
			HttpServletRequest httpRequest) {
		return ApiResponse.ok(AppTokenResponse.from(socialAuthService.completeSignup(request,
				httpRequest.getRemoteAddr())));
	}
}
