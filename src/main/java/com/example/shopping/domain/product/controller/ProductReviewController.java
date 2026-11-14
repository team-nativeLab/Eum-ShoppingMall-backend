package com.example.shopping.domain.product.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.product.dto.request.ProductReviewCreateRequest;
import com.example.shopping.domain.product.dto.response.ProductReviewResponse;
import com.example.shopping.domain.product.dto.response.ProductReviewSummaryResponse;
import com.example.shopping.domain.product.dto.response.ReviewableItemResponse;
import com.example.shopping.domain.product.service.ProductReviewService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.security.CurrentMember;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 상품 리뷰. 목록·요약은 누구나, 작성은 배송이 끝난 주문의 상품을 산 회원만 */
@Tag(name = "상품 리뷰")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductReviewController {

	private final ProductReviewService productReviewService;

	/** 리뷰 목록. sort = latest(기본) | rating_high | rating_low, size는 50까지 */
	@GetMapping("/products/{productId}/reviews")
	public ApiResponse<PageResponse<ProductReviewResponse>> reviews(@PathVariable Long productId,
			@RequestParam(required = false) String sort, @AuthenticationPrincipal Jwt jwt,
			@PageableDefault(size = 10) Pageable pageable) {
		return ApiResponse.ok(productReviewService.list(productId, sort, CurrentMember.idOrNull(jwt), pageable));
	}

	/** 평균 별점과 별점별 리뷰 수 */
	@GetMapping("/products/{productId}/reviews/summary")
	public ApiResponse<ProductReviewSummaryResponse> summary(@PathVariable Long productId) {
		return ApiResponse.ok(productReviewService.summary(productId));
	}

	/** 리뷰 작성. 주문 품목(orderItemId)마다 하나 */
	@PostMapping("/products/{productId}/reviews")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<ProductReviewResponse> create(@PathVariable Long productId, @AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody ProductReviewCreateRequest request) {
		return ApiResponse.ok(productReviewService.create(productId, CurrentMember.id(jwt), request));
	}

	/** 리뷰를 쓸 수 있는 내 주문 상품(배송 완료, 아직 리뷰 없음) */
	@GetMapping("/me/reviewable-items")
	public ApiResponse<List<ReviewableItemResponse>> reviewableItems(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.ok(productReviewService.reviewableItems(CurrentMember.id(jwt)));
	}
}
