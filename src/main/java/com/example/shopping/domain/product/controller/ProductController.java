package com.example.shopping.domain.product.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.product.dto.CategoryResponse;
import com.example.shopping.domain.product.dto.ProductDetailResponse;
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
	 * WEB-003, 004, 009 상품 목록·검색. 조건은 모두 선택이다.
	 * - 필터: categoryId, query(상품명·브랜드), brand(여러 개 가능), minPrice·maxPrice(원), certified(안전 인증 제품만)
	 * - 정렬: sort = recommended(기본) | price_asc | price_desc | popular
	 * - 페이지: page(0부터), size(기본 9, 최대 100)
	 */
	@GetMapping("/products")
	public ApiResponse<PageResponse<ProductSummaryResponse>> products(
			@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) List<String> brand,
			@RequestParam(required = false) Long minPrice,
			@RequestParam(required = false) Long maxPrice,
			@RequestParam(defaultValue = "false") boolean certified,
			@RequestParam(required = false) String sort,
			@PageableDefault(size = 9) Pageable pageable) {
		return ApiResponse.ok(productService.search(categoryId, query, brand, minPrice, maxPrice, certified, sort,
				pageable));
	}

	/** 브랜드 필터에 보여 줄 브랜드 목록. categoryId를 주면 그 카테고리에 상품이 있는 브랜드만 나온다. */
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
