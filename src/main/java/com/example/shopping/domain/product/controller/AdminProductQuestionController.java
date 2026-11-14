package com.example.shopping.domain.product.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.product.dto.request.AdminProductQuestionUpdateRequest;
import com.example.shopping.domain.product.dto.response.AdminProductQuestionResponse;
import com.example.shopping.domain.product.entity.ProductQuestionStatus;
import com.example.shopping.domain.product.service.AdminProductQuestionService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.security.CurrentMember;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 관리자 상품 문의 관리. 조회는 작성자 정보가 보이므로 감사 로그에 남는다(ApiPolicy.AUDITED_READS). */
@Tag(name = "상품 문의 관리")
@RestController
@RequestMapping("/api/v1/admin/product-questions")
@RequiredArgsConstructor
public class AdminProductQuestionController {

	private final AdminProductQuestionService adminProductQuestionService;

	/** 상품 문의 목록(?status=WAITING|ANSWERED&productId=) */
	@GetMapping
	public ApiResponse<PageResponse<AdminProductQuestionResponse>> questions(
			@RequestParam(required = false) ProductQuestionStatus status,
			@RequestParam(required = false) Long productId,
			@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
		return ApiResponse.ok(adminProductQuestionService.search(status, productId, pageable));
	}

	/** 상품 문의 상세. 작성자 정보가 보이므로 조회도 감사 로그에 남는다. */
	@GetMapping("/{questionId}")
	public ApiResponse<AdminProductQuestionResponse> question(@PathVariable Long questionId) {
		return ApiResponse.ok(adminProductQuestionService.get(questionId));
	}

	/** 답변·관리자 메모. 답변을 남기면 답변 완료, 빈 답변이면 답변 대기로 되돌린다. */
	@PatchMapping("/{questionId}")
	public ApiResponse<AdminProductQuestionResponse> update(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long questionId, @Valid @RequestBody AdminProductQuestionUpdateRequest request) {
		return ApiResponse.ok(adminProductQuestionService.update(CurrentMember.id(jwt), questionId, request));
	}
}
