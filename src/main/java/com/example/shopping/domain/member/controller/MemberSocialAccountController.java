package com.example.shopping.domain.member.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.member.dto.response.SocialAccountResponse;
import com.example.shopping.domain.member.entity.SocialProvider;
import com.example.shopping.domain.member.service.SocialAuthService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.security.CurrentMember;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 내 계정에 연결한 카카오·구글 계정 보기·연결 끊기(#225) */
@Tag(name = "내 정보")
@RestController
@RequestMapping("/api/v1/me/social-accounts")
@RequiredArgsConstructor
public class MemberSocialAccountController {

	private final SocialAuthService socialAuthService;

	/** 연결한 카카오·구글 계정. 연결한 순서대로 */
	@GetMapping
	public ApiResponse<List<SocialAccountResponse>> accounts(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.ok(socialAuthService.accounts(CurrentMember.id(jwt)));
	}

	/**
	 * 카카오(kakao)·구글(google) 연결 끊기. 비밀번호가 없는 계정의 마지막 연결이면 로그인할 방법이 없어지므로 409
	 * (SOCIAL_UNLINK_NOT_ALLOWED). 연결하지 않은 제공자면 404(SOCIAL_ACCOUNT_NOT_LINKED)
	 */
	@DeleteMapping("/{provider}")
	public ApiResponse<Void> unlink(@AuthenticationPrincipal Jwt jwt, @PathVariable String provider) {
		socialAuthService.unlink(CurrentMember.id(jwt), SocialProvider.fromPath(provider));
		return ApiResponse.ok(null);
	}
}
