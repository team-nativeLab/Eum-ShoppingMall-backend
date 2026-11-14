package com.example.shopping.domain.home.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.home.dto.response.ContentResponse;
import com.example.shopping.domain.home.dto.request.SiteContentUpsertRequest;
import com.example.shopping.domain.home.service.AdminContentService;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "고정 콘텐츠 관리")
@RestController
@RequestMapping("/api/v1/admin/contents")
@RequiredArgsConstructor
public class AdminContentController {

	private final AdminContentService adminContentService;

	/** 저장된 고정 콘텐츠 목록(company, terms, privacy-policy) */
	@GetMapping
	public ApiResponse<List<ContentResponse>> list() {
		return ApiResponse.ok(adminContentService.list());
	}

	/** 고정 콘텐츠 하나. 저장하지 않았으면 404(CONTENT_NOT_FOUND) */
	@GetMapping("/{code}")
	public ApiResponse<ContentResponse> get(@PathVariable String code) {
		return ApiResponse.ok(adminContentService.get(code));
	}

	/** 고정 콘텐츠를 만들거나 고친다. 본문은 평문이며 HTML 태그는 400 */
	@PutMapping("/{code}")
	public ApiResponse<ContentResponse> upsert(@PathVariable String code,
			@Valid @RequestBody SiteContentUpsertRequest request) {
		return ApiResponse.ok(adminContentService.upsert(code, request));
	}
}
