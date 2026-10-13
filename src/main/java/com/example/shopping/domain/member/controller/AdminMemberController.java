package com.example.shopping.domain.member.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.member.dto.AdminMemberDetailResponse;
import com.example.shopping.domain.member.dto.AdminMemberSummaryResponse;
import com.example.shopping.domain.member.service.AdminMemberService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.response.PageResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminMemberController {

	private final AdminMemberService adminMemberService;

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
}
