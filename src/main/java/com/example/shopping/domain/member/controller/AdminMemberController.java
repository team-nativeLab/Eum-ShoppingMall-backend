package com.example.shopping.domain.member.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.example.shopping.domain.member.dto.response.AdminMemberDetailResponse;
import com.example.shopping.domain.member.dto.request.AdminMemberRoleUpdateRequest;
import com.example.shopping.domain.member.dto.request.AdminMemberStatusUpdateRequest;
import com.example.shopping.domain.member.dto.response.AdminMemberSummaryResponse;
import com.example.shopping.domain.member.service.AdminMemberService;
import com.example.shopping.domain.member.service.AdminMfaService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.security.CurrentMember;

import lombok.RequiredArgsConstructor;

@Tag(name = "회원 관리")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminMemberController {

	private final AdminMemberService adminMemberService;
	private final AdminMfaService adminMfaService;

	/** ADM-012 회원 목록 (?keyword=로 이메일·이름 검색) */
	@GetMapping
	public ApiResponse<PageResponse<AdminMemberSummaryResponse>> users(
			@RequestParam(required = false) String keyword,
			@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
		return ApiResponse.ok(adminMemberService.search(keyword, pageable));
	}

	/** ADM-013 회원 상세 + 최근 주문 이력 */
	@GetMapping("/{userId}")
	public ApiResponse<AdminMemberDetailResponse> user(@PathVariable Long userId) {
		return ApiResponse.ok(adminMemberService.getDetail(userId));
	}

	/** 최고 관리자가 회원의 일반·관리자 역할을 바꾼다. */
	@PatchMapping("/{userId}/role")
	public ApiResponse<AdminMemberDetailResponse> changeRole(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long userId, @Valid @RequestBody AdminMemberRoleUpdateRequest request) {
		return ApiResponse.ok(adminMemberService.changeRole(CurrentMember.id(jwt), userId, request.role()));
	}

	/** 최고 관리자가 관리자의 OTP를 지운다(기기 분실·교체, #164). 그 관리자는 다시 로그인해 새로 등록한다. */
	@DeleteMapping("/{userId}/mfa")
	public ApiResponse<Void> resetMfa(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
		adminMfaService.reset(CurrentMember.id(jwt), userId);
		return ApiResponse.ok(null);
	}

	/** 회원 정지(SUSPENDED)·해제(ACTIVE). 정지할 때는 사유가 필요하다. */
	@PatchMapping("/{userId}/status")
	public ApiResponse<AdminMemberDetailResponse> changeStatus(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long userId, @Valid @RequestBody AdminMemberStatusUpdateRequest request) {
		return ApiResponse.ok(adminMemberService.changeStatus(CurrentMember.id(jwt), userId, request));
	}
}
