package com.example.shopping.domain.home.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.home.dto.HomeLayout;
import com.example.shopping.domain.home.service.AdminHomeLayoutService;
import com.example.shopping.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 관리자 홈 화면 편성(히어로, 프로모션, 서비스 안내, 베스트 상품, 견적 문의 띠). 화면 단위로 통째로 저장한다. */
@RestController
@RequestMapping("/api/v1/admin/home")
@RequiredArgsConstructor
public class AdminHomeLayoutController {

	private final AdminHomeLayoutService adminHomeLayoutService;

	/** 저장한 홈 편성. 저장한 적이 없으면 빈 목록과 null(견적 문의 띠) */
	@GetMapping
	public ApiResponse<HomeLayout> get() {
		return ApiResponse.ok(adminHomeLayoutService.get());
	}

	/** 홈 편성 전체를 바꾼다. 저장 즉시 고객 홈에 반영된다. */
	@PutMapping
	public ApiResponse<HomeLayout> save(@Valid @RequestBody HomeLayout layout) {
		return ApiResponse.ok(adminHomeLayoutService.save(layout));
	}
}
