package com.example.shopping.domain.member.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.member.dto.response.MeResponse;
import com.example.shopping.domain.member.dto.request.PasswordChangeRequest;
import com.example.shopping.domain.member.dto.request.ProfileUpdateRequest;
import com.example.shopping.domain.member.service.MemberService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.security.CurrentMember;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;

	/** WEB-020 마이페이지 조회 */
	@GetMapping
	public ApiResponse<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.ok(memberService.getMe(CurrentMember.id(jwt)));
	}

	/** WEB-020 내 정보 수정 (이름·연락처) */
	@PatchMapping
	public ApiResponse<MeResponse> updateProfile(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody ProfileUpdateRequest request) {
		return ApiResponse.ok(memberService.updateProfile(CurrentMember.id(jwt), request));
	}

	/** WEB-020 비밀번호 변경. 현재 비밀번호를 확인한 뒤 바꾸고, 다른 기기의 로그인을 모두 끊는다. */
	@PatchMapping("/password")
	public ApiResponse<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody PasswordChangeRequest request) {
		memberService.changePassword(CurrentMember.id(jwt), request);
		return ApiResponse.ok(null);
	}

	@DeleteMapping
	public ApiResponse<Void> withdraw(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody com.example.shopping.domain.member.dto.request.AccountWithdrawalRequest request) {
		memberService.withdraw(CurrentMember.id(jwt), request.password());
		return ApiResponse.ok(null);
	}
}
