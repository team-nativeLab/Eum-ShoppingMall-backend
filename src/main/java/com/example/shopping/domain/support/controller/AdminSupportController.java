package com.example.shopping.domain.support.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.support.dto.response.AdminInquiryDetailResponse;
import com.example.shopping.domain.support.dto.response.AdminInquirySummaryResponse;
import com.example.shopping.domain.support.dto.response.AdminNoticeResponse;
import com.example.shopping.domain.support.dto.request.FaqCreateRequest;
import com.example.shopping.domain.support.dto.response.FaqResponse;
import com.example.shopping.domain.support.dto.request.FaqUpdateRequest;
import com.example.shopping.domain.support.dto.request.InquiryAnswerRequest;
import com.example.shopping.domain.support.dto.request.NoticeCreateRequest;
import com.example.shopping.domain.support.dto.request.NoticeUpdateRequest;
import com.example.shopping.domain.support.entity.InquiryStatus;
import com.example.shopping.domain.support.service.AdminSupportService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.response.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminSupportController {

	private final AdminSupportService adminSupportService;

	/** 전체 공지 목록 (비공개 포함, 고정 공지 먼저) */
	@GetMapping("/notices")
	public ApiResponse<PageResponse<AdminNoticeResponse>> notices(@PageableDefault(size = 20) Pageable pageable) {
		return ApiResponse.ok(adminSupportService.getNotices(pageable));
	}

	/** 공지 등록 (상단 고정·공개 여부 지정) */
	@PostMapping("/notices")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<AdminNoticeResponse> createNotice(@Valid @RequestBody NoticeCreateRequest request) {
		return ApiResponse.ok(adminSupportService.createNotice(request));
	}

	/** 공지 수정. 잠시 내릴 때는 삭제 대신 비공개로 전환한다. */
	@PatchMapping("/notices/{noticeId}")
	public ApiResponse<AdminNoticeResponse> updateNotice(@PathVariable Long noticeId,
			@Valid @RequestBody NoticeUpdateRequest request) {
		return ApiResponse.ok(adminSupportService.updateNotice(noticeId, request));
	}

	@DeleteMapping("/notices/{noticeId}")
	public ApiResponse<Void> deleteNotice(@PathVariable Long noticeId) {
		adminSupportService.deleteNotice(noticeId);
		return ApiResponse.ok(null);
	}

	/** FAQ 등록 (분류·질문·답변·표시 순서). 목록은 고객용 GET /api/v1/faqs를 함께 쓴다. */
	@PostMapping("/faqs")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<FaqResponse> createFaq(@Valid @RequestBody FaqCreateRequest request) {
		return ApiResponse.ok(adminSupportService.createFaq(request));
	}

	@PatchMapping("/faqs/{faqId}")
	public ApiResponse<FaqResponse> updateFaq(@PathVariable Long faqId,
			@Valid @RequestBody FaqUpdateRequest request) {
		return ApiResponse.ok(adminSupportService.updateFaq(faqId, request));
	}

	@DeleteMapping("/faqs/{faqId}")
	public ApiResponse<Void> deleteFaq(@PathVariable Long faqId) {
		adminSupportService.deleteFaq(faqId);
		return ApiResponse.ok(null);
	}

	/** 1:1 문의 목록 (?status=RECEIVED|ANSWERED) */
	@GetMapping("/inquiries")
	public ApiResponse<PageResponse<AdminInquirySummaryResponse>> inquiries(
			@RequestParam(required = false) InquiryStatus status,
			@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
		return ApiResponse.ok(adminSupportService.getInquiries(status, pageable));
	}

	/** 1:1 문의 상세 (내용·연락 정보·기존 답변) */
	@GetMapping("/inquiries/{inquiryId}")
	public ApiResponse<AdminInquiryDetailResponse> inquiry(@PathVariable Long inquiryId) {
		return ApiResponse.ok(adminSupportService.getInquiry(inquiryId));
	}

	/** 1:1 문의 답변 등록. 상태를 ANSWERED로 바꾼다. */
	@PatchMapping("/inquiries/{inquiryId}")
	public ApiResponse<AdminInquiryDetailResponse> answerInquiry(@PathVariable Long inquiryId,
			@Valid @RequestBody InquiryAnswerRequest request) {
		return ApiResponse.ok(adminSupportService.answerInquiry(inquiryId, request.answer()));
	}
}
