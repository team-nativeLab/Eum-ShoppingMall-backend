package com.example.shopping.domain.product.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.product.dto.request.BrandRequest;
import com.example.shopping.domain.product.dto.response.BrandResponse;
import com.example.shopping.domain.product.service.AdminBrandService;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 관리자 브랜드 관리 */
@Tag(name = "브랜드 관리")
@RestController
@RequestMapping("/api/v1/admin/brands")
@RequiredArgsConstructor
public class AdminBrandController {

	private final AdminBrandService adminBrandService;

	/** 숨긴 브랜드를 포함한 전체 목록 */
	@GetMapping
	public ApiResponse<List<BrandResponse.Admin>> brands() {
		return ApiResponse.ok(adminBrandService.list());
	}

	/** 브랜드 등록. 같은 이름의 브랜드가 있으면 409(BRAND_NAME_DUPLICATED) */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<BrandResponse.Admin> create(@Valid @RequestBody BrandRequest request) {
		return ApiResponse.ok(adminBrandService.create(request));
	}

	/** 전체 수정. 이름을 바꾸면 이전 이름을 쓰던 상품의 브랜드도 함께 바꾼다. */
	@PutMapping("/{brandId}")
	public ApiResponse<BrandResponse.Admin> update(@PathVariable Long brandId,
			@Valid @RequestBody BrandRequest request) {
		return ApiResponse.ok(adminBrandService.update(brandId, request));
	}

	/** 브랜드 소개를 지운다. 상품의 브랜드 값은 그대로다. */
	@DeleteMapping("/{brandId}")
	public ApiResponse<Void> delete(@PathVariable Long brandId) {
		adminBrandService.delete(brandId);
		return ApiResponse.ok(null);
	}
}
