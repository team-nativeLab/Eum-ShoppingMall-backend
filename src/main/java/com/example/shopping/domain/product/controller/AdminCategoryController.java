package com.example.shopping.domain.product.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.product.dto.response.AdminCategoryResponse;
import com.example.shopping.domain.product.dto.request.CategoryCreateRequest;
import com.example.shopping.domain.product.dto.request.CategoryUpdateRequest;
import com.example.shopping.domain.product.service.AdminCategoryService;
import com.example.shopping.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** ADM-008 카테고리 관리 */
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

	private final AdminCategoryService adminCategoryService;

	/** 관리용 카테고리 전체 목록 (비노출 포함) */
	@GetMapping
	public ApiResponse<List<AdminCategoryResponse>> categories() {
		return ApiResponse.ok(adminCategoryService.getAll());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<AdminCategoryResponse> createCategory(@Valid @RequestBody CategoryCreateRequest request) {
		return ApiResponse.ok(adminCategoryService.create(request));
	}

	/** 이름·정렬 순서·노출 여부(active) 수정 */
	@PatchMapping("/{categoryId}")
	public ApiResponse<AdminCategoryResponse> updateCategory(@PathVariable Long categoryId,
			@Valid @RequestBody CategoryUpdateRequest request) {
		return ApiResponse.ok(adminCategoryService.update(categoryId, request));
	}

	/** 하위 카테고리나 소속 상품이 있으면 409로 거부하고, 비어 있을 때만 삭제한다. */
	@DeleteMapping("/{categoryId}")
	public ApiResponse<Void> deleteCategory(@PathVariable Long categoryId) {
		adminCategoryService.delete(categoryId);
		return ApiResponse.ok(null);
	}
}
