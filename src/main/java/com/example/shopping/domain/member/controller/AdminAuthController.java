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
import com.example.shopping.domain.member.dto.AuthTokens;
import com.example.shopping.domain.member.dto.request.AdminMfaTokenRequest;
import com.example.shopping.domain.member.dto.request.AdminMfaVerifyRequest;
import com.example.shopping.domain.member.dto.request.LoginRequest;
import com.example.shopping.domain.member.dto.response.AdminLoginResponse;
import com.example.shopping.domain.member.dto.response.AdminMfaSetupResponse;
import com.example.shopping.domain.member.dto.response.LoginResponse;
import com.example.shopping.domain.member.service.AdminMfaService;
import com.example.shopping.domain.member.service.AuthService;
import com.example.shopping.domain.member.service.AuthService.AdminLoginResult;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 세션은 회원 세션과 따로 둔다(#163). Refresh 쿠키(admin_refresh_token)는 이 경로로만 가고,
 * 재발급·로그아웃은 관리자 화면 주소(CORS_ADMIN_ORIGINS)에서 온 요청만 받는다(RefreshCookieOriginFilter).
 * 로그인은 비밀번호와 OTP 두 단계다(#164). 세션은 30분 동안 쓰지 않거나 로그인한 지 8시간이 지나면 끝난다.
 */
@Tag(name = "관리자 인증")
@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

	private final AuthService authService;
	private final AdminMfaService adminMfaService;
	private final AuthTokenResponder responder;

	/**
	 * ADM-001 관리자 로그인 1단계(비밀번호). 권한이 ADMIN인 계정만 받는다.
	 * OTP를 요구하면 쿠키 없이 mfaToken을 주고(mfaSetupRequired면 등록부터), 요구하지 않으면 토큰을 준다.
	 */
	@PostMapping("/login")
	public ResponseEntity<ApiResponse<AdminLoginResponse>> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest) {
		AdminLoginResult result = authService.adminLogin(request, httpRequest.getRemoteAddr());
		if (result.mfaChallenge() != null) {
			AdminMfaService.Challenge challenge = result.mfaChallenge();
			return ResponseEntity.ok(ApiResponse.ok(AdminLoginResponse.mfa(challenge.mfaToken(),
					challenge.expiresIn(), challenge.setupRequired())));
		}
		AuthTokens tokens = result.tokens();
		return responder.withTokens(Session.ADMIN, tokens,
				AdminLoginResponse.authenticated(tokens.accessToken(), tokens.expiresIn()));
	}

	/** OTP 앱에 등록할 비밀 키와 QR 주소. OTP를 아직 등록하지 않은 관리자만 받는다(이미 등록했으면 409). */
	@PostMapping("/mfa/setup")
	public ApiResponse<AdminMfaSetupResponse> setupMfa(@Valid @RequestBody AdminMfaTokenRequest request) {
		return ApiResponse.ok(adminMfaService.setup(request.mfaToken()));
	}

	/** 관리자 로그인 2단계(OTP). 코드를 확인하고 토큰을 준다. 처음 넣은 코드면 OTP 등록도 이때 끝난다. */
	@PostMapping("/mfa/verify")
	public ResponseEntity<ApiResponse<LoginResponse>> verifyMfa(@Valid @RequestBody AdminMfaVerifyRequest request,
			HttpServletRequest httpRequest) {
		return responder.withTokens(Session.ADMIN,
				authService.verifyAdminMfa(request.mfaToken(), request.code(), httpRequest.getRemoteAddr()));
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
