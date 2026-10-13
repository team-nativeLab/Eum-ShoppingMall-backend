package com.example.shopping.domain.home.controller;

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

import com.example.shopping.domain.home.dto.BannerCreateRequest;
import com.example.shopping.domain.home.dto.BannerResponse;
import com.example.shopping.domain.home.dto.BannerUpdateRequest;
import com.example.shopping.domain.home.service.AdminBannerService;
import com.example.shopping.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/banners")
@RequiredArgsConstructor
public class AdminBannerController {

	private final AdminBannerService adminBannerService;

	/** 전체 배너 목록 (비노출 포함) */
	@GetMapping
	public ApiResponse<List<BannerResponse>> banners() {
		return ApiResponse.ok(adminBannerService.getAll());
	}

	/** 배너 등록 (이미지·연결 대상·노출 순서) */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<BannerResponse> createBanner(@Valid @RequestBody BannerCreateRequest request) {
		return ApiResponse.ok(adminBannerService.create(request));
	}

	@PatchMapping("/{bannerId}")
	public ApiResponse<BannerResponse> updateBanner(@PathVariable Long bannerId,
			@Valid @RequestBody BannerUpdateRequest request) {
		return ApiResponse.ok(adminBannerService.update(bannerId, request));
	}

	@DeleteMapping("/{bannerId}")
	public ApiResponse<Void> deleteBanner(@PathVariable Long bannerId) {
		adminBannerService.delete(bannerId);
		return ApiResponse.ok(null);
	}
}
