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

import com.example.shopping.domain.product.dto.CategoryResponse;
import com.example.shopping.domain.product.dto.ProductDetailResponse;
import com.example.shopping.domain.product.dto.ProductFacetsResponse;
import com.example.shopping.domain.product.dto.ProductSearchRequest;
import com.example.shopping.domain.product.dto.ProductSuggestionResponse;
import com.example.shopping.domain.product.dto.ProductSummaryResponse;
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

	/** 브랜드 필터에 보여 줄 브랜드 목록. categoryId를 주면 그 카테고리(하위 포함)에 상품이 있는 브랜드만 나온다. */
	@GetMapping("/products/brands")
	public ApiResponse<List<String>> brands(@RequestParam(required = false) Long categoryId) {
		return ApiResponse.ok(productService.getBrands(categoryId));
	}

	/** WEB-005, 006 상품 상세 */
	@GetMapping("/products/{productId}")
	public ApiResponse<ProductDetailResponse> product(@PathVariable Long productId) {
		return ApiResponse.ok(productService.getDetail(productId));
	}
}
