package com.example.shopping.domain.product.controller;

import org.springframework.data.domain.Pageable;
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

import com.example.shopping.domain.product.dto.request.ProductQuestionCreateRequest;
import com.example.shopping.domain.product.dto.response.ProductQuestionResponse;
import com.example.shopping.domain.product.service.ProductQuestionService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.security.CurrentMember;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 상품 상세의 상품 문의(Q&A) */
@RestController
@RequestMapping("/api/v1/products/{productId}/questions")
@RequiredArgsConstructor
public class ProductQuestionController {

	private final ProductQuestionService productQuestionService;

	/** 문의 목록(누구나). 작성자 이름은 가리고, 비밀 문의는 작성자 본인만 내용과 답변을 본다. size는 50까지 */
	@GetMapping
	public ApiResponse<PageResponse<ProductQuestionResponse>> questions(@PathVariable Long productId,
			@AuthenticationPrincipal Jwt jwt, @PageableDefault(size = 10) Pageable pageable) {
		return ApiResponse.ok(productQuestionService.list(productId, CurrentMember.idOrNull(jwt), pageable));
	}

	/** 문의 작성(회원). 고객에게 보이는 상품에만 남길 수 있다. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<ProductQuestionResponse> create(@PathVariable Long productId, @AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody ProductQuestionCreateRequest request) {
		return ApiResponse.ok(productQuestionService.create(productId, CurrentMember.id(jwt), request));
	}
}
