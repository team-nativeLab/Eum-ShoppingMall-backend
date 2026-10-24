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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/contents")
@RequiredArgsConstructor
public class AdminContentController {

	private final AdminContentService adminContentService;

	@GetMapping
	public ApiResponse<List<ContentResponse>> list() {
		return ApiResponse.ok(adminContentService.list());
	}

	@GetMapping("/{code}")
	public ApiResponse<ContentResponse> get(@PathVariable String code) {
		return ApiResponse.ok(adminContentService.get(code));
	}

	@PutMapping("/{code}")
	public ApiResponse<ContentResponse> upsert(@PathVariable String code,
			@Valid @RequestBody SiteContentUpsertRequest request) {
		return ApiResponse.ok(adminContentService.upsert(code, request));
	}
}
