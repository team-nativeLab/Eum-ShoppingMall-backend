package com.example.shopping.domain.support.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.support.dto.response.FaqResponse;
import com.example.shopping.domain.support.dto.request.InquiryCreateRequest;
import com.example.shopping.domain.support.dto.response.InquiryResponse;
import com.example.shopping.domain.support.dto.response.NoticeResponse;
import com.example.shopping.domain.support.service.SupportService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.security.CurrentMember;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SupportController {

	private final SupportService supportService;

	/** WEB-022 공지 목록 */
	@GetMapping("/notices")
	public ApiResponse<PageResponse<NoticeResponse>> notices(@PageableDefault(size = 20) Pageable pageable) {
		return ApiResponse.ok(supportService.getNotices(pageable));
	}

	/** WEB-022 공지 상세 */
	@GetMapping("/notices/{noticeId}")
	public ApiResponse<NoticeResponse> notice(@PathVariable Long noticeId) {
		return ApiResponse.ok(supportService.getNotice(noticeId));
	}

	/** WEB-023 FAQ 조회 */
	@GetMapping("/faqs")
	public ApiResponse<List<FaqResponse>> faqs() {
		return ApiResponse.ok(supportService.getFaqs());
	}

	/** WEB-024 1:1 문의 접수. 비회원도 가능하며, 로그인 상태면 내 문의 내역에 남는다. */
	@PostMapping("/support/inquiries")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<InquiryResponse> createInquiry(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody InquiryCreateRequest request) {
		return ApiResponse.ok(supportService.createInquiry(CurrentMember.idOrNull(jwt), request));
	}

	/** WEB-024 내 1:1 문의 내역과 답변. 비회원으로 접수한 건은 조회할 수 없다. */
	@GetMapping("/support/inquiries")
	public ApiResponse<PageResponse<InquiryResponse>> myInquiries(@AuthenticationPrincipal Jwt jwt,
			@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
		return ApiResponse.ok(supportService.getMyInquiries(CurrentMember.id(jwt), pageable));
	}
}
