package com.example.shopping.domain.member.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.member.controller.AuthTokenResponder.Session;
import com.example.shopping.domain.member.dto.AuthTokens;
import com.example.shopping.domain.member.dto.request.SocialLoginRequest;
import com.example.shopping.domain.member.dto.request.SocialSignupRequest;
import com.example.shopping.domain.member.dto.response.LoginResponse;
import com.example.shopping.domain.member.dto.response.SocialLoginResponse;
import com.example.shopping.domain.member.entity.SocialProvider;
import com.example.shopping.domain.member.service.SocialAuthService;
import com.example.shopping.domain.member.service.SocialLoginResult;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 웹 카카오·구글 로그인(#225). 프론트가 카카오·구글 로그인 화면에서 받은 인가 코드를 보내면 서버가 카카오·구글에 확인한다.
 * 로그인하면 이메일 로그인과 같이 Access 토큰은 본문, Refresh 토큰은 HttpOnly 쿠키로 준다.
 */
@Tag(name = "인증")
@RestController
@RequestMapping("/api/v1/auth/social")
@RequiredArgsConstructor
public class SocialAuthController {

	private final SocialAuthService socialAuthService;
	private final AuthTokenResponder responder;

	/**
	 * 카카오(kakao)·구글(google) 로그인. 같은 이메일의 회원이 있으면 카카오·구글이 인증한 이메일일 때만 연결한다.
	 * 처음 온 사람이면 signupRequired와 가입 토큰을 준다(이때는 쿠키가 없다). 관리자 계정은 쓸 수 없다.
	 */
	@PostMapping("/{provider}/login")
	public ResponseEntity<ApiResponse<SocialLoginResponse>> login(@PathVariable String provider,
			@Valid @RequestBody SocialLoginRequest request, HttpServletRequest httpRequest) {
		SocialLoginResult result = socialAuthService.loginWithCode(SocialProvider.fromPath(provider),
				request.code(), request.redirectUri(), request.codeVerifier(), httpRequest.getRemoteAddr());
		if (result.signupRequired()) {
			return ResponseEntity.ok(ApiResponse.ok(SocialLoginResponse.signupRequired(result.signup())));
		}
		AuthTokens tokens = result.tokens();
		return responder.withTokens(Session.MEMBER, tokens,
				SocialLoginResponse.loggedIn(tokens.accessToken(), tokens.expiresIn()));
	}

	/** 카카오·구글로 처음 온 사람의 가입. 약관·개인정보 동의를 받아 가입하고 바로 로그인한다(Refresh 토큰은 쿠키). */
	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<LoginResponse>> signup(@Valid @RequestBody SocialSignupRequest request,
			HttpServletRequest httpRequest) {
		return responder.withTokens(Session.MEMBER,
				socialAuthService.completeSignup(request, httpRequest.getRemoteAddr()));
	}
}
