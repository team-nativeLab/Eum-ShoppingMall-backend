package com.example.shopping.domain.product.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.product.dto.response.BrandResponse;
import com.example.shopping.domain.product.service.BrandService;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 브랜드 목록·상세(누구나). 브랜드의 상품은 GET /api/v1/products?brand={name}으로 본다. */
@Tag(name = "브랜드")
@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandController {

	private final BrandService brandService;

	/** 보이는 브랜드 목록. 정렬 순서·이름 순 */
	@GetMapping
	public ApiResponse<List<BrandResponse.Summary>> brands() {
		return ApiResponse.ok(brandService.list());
	}

	/** 대표 브랜드. 없으면 data가 null */
	@GetMapping("/featured")
	public ApiResponse<BrandResponse.Detail> featured() {
		return ApiResponse.ok(brandService.featured());
	}

	/** 브랜드 상세. 숨긴 브랜드는 404(BRAND_NOT_FOUND) */
	@GetMapping("/{brandId}")
	public ApiResponse<BrandResponse.Detail> brand(@PathVariable Long brandId) {
		return ApiResponse.ok(brandService.get(brandId));
	}
}
