package com.example.shopping.domain.product.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.product.dto.response.CategoryResponse;
import com.example.shopping.domain.product.dto.response.ProductDetailResponse;
import com.example.shopping.domain.product.dto.response.ProductFacetsResponse;
import com.example.shopping.domain.product.dto.request.ProductSearchRequest;
import com.example.shopping.domain.product.dto.response.ProductSuggestionResponse;
import com.example.shopping.domain.product.dto.response.ProductSummaryResponse;
import com.example.shopping.domain.product.service.ProductService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.response.PageResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductController {

	private final ProductService productService;

	/** WEB-003, WEB-021 카테고리 목록 (전체 메뉴에서도 재사용) */
	@GetMapping("/categories")
	public ApiResponse<List<CategoryResponse>> categories() {
		return ApiResponse.ok(productService.getCategories());
	}

	/**
	 * WEB-003, 004, 009 상품 목록·검색. 조건은 모두 선택이다 (ProductSearchRequest 참고).
	 * - 필터: categoryId(하위 카테고리 포함), query(상품명·브랜드), brand(여러 개), minPrice·maxPrice(원),
	 *   certification(인증 종류, 여러 개), certified(인증 제품만), excludeSoldOut(품절 제외)
	 * - 정렬: sort = recommended(기본) | newest | price_asc | price_desc | popular
	 * - 페이지: page(0부터), size(기본 24, 최대 100)
	 */
	@GetMapping("/products")
	public ApiResponse<PageResponse<ProductSummaryResponse>> products(@ModelAttribute ProductSearchRequest request,
			@PageableDefault(size = 24) Pageable pageable) {
		return ApiResponse.ok(productService.search(request, pageable));
	}

	/**
	 * 검색 화면의 카테고리 탭과 필터(브랜드, 인증)에 보여 줄 항목별 상품 수.
	 * 목록 조회와 같은 categoryId·query를 넘긴다.
	 */
	@GetMapping("/products/facets")
	public ApiResponse<ProductFacetsResponse> facets(@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) String query) {
		return ApiResponse.ok(productService.getFacets(categoryId, query));
	}

	/** 검색창 자동 완성. 상품명이나 브랜드에 검색어가 든 상품 최대 5개 */
	@GetMapping("/products/suggestions")
	public ApiResponse<List<ProductSuggestionResponse>> suggestions(@RequestParam(required = false) String query) {
		return ApiResponse.ok(productService.suggest(query));
	}

	/** 검색 결과가 없을 때의 추천 상품. 고객에게 보이는 상품을 추천순(관리자 추천 점수 → 최근 등록)으로 limit개(기본 10, 최대 20) */
	@GetMapping("/products/recommended")
	public ApiResponse<List<ProductSummaryResponse>> recommended(
			@RequestParam(defaultValue = "" + ProductService.DEFAULT_COLLECTION_LIMIT) int limit) {
		return ApiResponse.ok(productService.getRecommended(limit));
	}

	/** WEB-005, 006 상품 상세 */
	@GetMapping("/products/{productId}")
	public ApiResponse<ProductDetailResponse> product(@PathVariable Long productId) {
		return ApiResponse.ok(productService.getDetail(productId));
	}

	/**
	 * 상품 상세의 "함께 구매하면 좋은 제품". 같은 최상위 카테고리의 상품을 추천순으로, 모자라면 전체 추천순으로 채워
	 * limit개(기본 10, 최대 20). 기준 상품은 빠지고, 고객에게 보이지 않는 기준 상품이면 404다.
	 */
	@GetMapping("/products/{productId}/related")
	public ApiResponse<List<ProductSummaryResponse>> related(@PathVariable Long productId,
			@RequestParam(defaultValue = "" + ProductService.DEFAULT_COLLECTION_LIMIT) int limit) {
		return ApiResponse.ok(productService.getRelated(productId, limit));
	}
}
