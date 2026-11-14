package com.example.shopping.domain.product.controller;

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

import com.example.shopping.domain.product.dto.request.AdminProductReviewUpdateRequest;
import com.example.shopping.domain.product.dto.response.AdminProductReviewResponse;
import com.example.shopping.domain.product.service.AdminProductReviewService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.response.PageResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 관리자 리뷰 관리. 목록은 작성자 이름이 보이므로 감사 로그에 남는다(ApiPolicy.AUDITED_READS). */
@Tag(name = "리뷰 관리")
@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
public class AdminProductReviewController {

	private final AdminProductReviewService adminProductReviewService;

	/** 리뷰 목록(?productId=&visible=&rating=). 정렬은 id·createdAt·rating */
	@GetMapping
	public ApiResponse<PageResponse<AdminProductReviewResponse>> reviews(
			@RequestParam(required = false) Long productId,
			@RequestParam(required = false) Boolean visible,
			@RequestParam(required = false) Integer rating,
			@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
		return ApiResponse.ok(adminProductReviewService.search(productId, visible, rating, pageable));
	}

	/** 숨기기·다시 보이기(visible), 답변(adminReply), 메모(adminMemo). 보낸 항목만 바꾼다. */
	@PatchMapping("/{reviewId}")
	public ApiResponse<AdminProductReviewResponse> update(@PathVariable Long reviewId,
			@Valid @RequestBody AdminProductReviewUpdateRequest request) {
		return ApiResponse.ok(adminProductReviewService.update(reviewId, request));
	}
}
