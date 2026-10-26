package com.example.shopping.domain.member.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.member.dto.request.LoginRequest;
import com.example.shopping.domain.member.dto.response.LoginResponse;
import com.example.shopping.domain.member.service.AuthService;
import com.example.shopping.global.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

	private final AuthService authService;
	private final AuthTokenResponder responder;

	/** ADM-001 관리자 로그인. 권한이 ADMIN인 계정만 토큰을 받는다. 재발급·로그아웃은 회원과 같은 API를 쓴다. */
	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest) {
		return responder.withTokens(authService.adminLogin(request, httpRequest.getRemoteAddr()));
	}
}
