package com.example.shopping.domain.quote.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.quote.dto.QuoteCreateRequest;
import com.example.shopping.domain.quote.dto.QuoteResponse;
import com.example.shopping.domain.quote.service.QuoteService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.security.CurrentMember;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
public class QuoteController {

	private final QuoteService quoteService;

	/** WEB-026 견적 문의 접수. 비회원도 가능하며, 로그인 상태면 내 견적 문의 내역에 남는다. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<QuoteResponse> createQuote(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody QuoteCreateRequest request) {
		return ApiResponse.ok(quoteService.create(CurrentMember.idOrNull(jwt), request));
	}

	/** 내 견적 문의 내역. 로그인 상태로 접수한 건만 조회된다. */
	@GetMapping
	public ApiResponse<PageResponse<QuoteResponse>> myQuotes(@AuthenticationPrincipal Jwt jwt,
			@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
		return ApiResponse.ok(quoteService.getMyQuotes(CurrentMember.id(jwt), pageable));
	}
}
