package com.example.shopping.domain.quote.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.quote.dto.response.AdminQuoteResponse;
import com.example.shopping.domain.quote.dto.request.QuoteStatusUpdateRequest;
import com.example.shopping.domain.quote.entity.QuoteStatus;
import com.example.shopping.domain.quote.service.AdminQuoteService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.response.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** ADM-009 견적 문의 관리 */
@RestController
@RequestMapping("/api/v1/admin/quotes")
@RequiredArgsConstructor
public class AdminQuoteController {

	private final AdminQuoteService adminQuoteService;

	/** 견적 문의 목록 (?status=RECEIVED|IN_PROGRESS|COMPLETED) */
	@GetMapping
	public ApiResponse<PageResponse<AdminQuoteResponse>> quotes(
			@RequestParam(required = false) QuoteStatus status,
			@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
		return ApiResponse.ok(adminQuoteService.getQuotes(status, pageable));
	}

	/** 처리 상태 변경 */
	@PatchMapping("/{quoteId}")
	public ApiResponse<AdminQuoteResponse> updateQuote(@PathVariable Long quoteId,
			@Valid @RequestBody QuoteStatusUpdateRequest request) {
		return ApiResponse.ok(adminQuoteService.changeStatus(quoteId, request.status()));
	}
}
