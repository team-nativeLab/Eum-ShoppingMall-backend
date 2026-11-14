package com.example.shopping.domain.home.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.home.dto.CompanyInfo;
import com.example.shopping.domain.home.service.CompanyInfoService;
import com.example.shopping.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 관리자 사업자·고객센터 정보(푸터) */
@RestController
@RequestMapping("/api/v1/admin/company")
@RequiredArgsConstructor
public class AdminCompanyInfoController {

	private final CompanyInfoService companyInfoService;

	/** 저장한 사업자 정보. 저장한 적이 없으면 data가 null */
	@GetMapping
	public ApiResponse<CompanyInfo> get() {
		return ApiResponse.ok(companyInfoService.getForAdmin());
	}

	/** 사업자 정보 전체를 바꾼다. */
	@PutMapping
	public ApiResponse<CompanyInfo> save(@Valid @RequestBody CompanyInfo companyInfo) {
		return ApiResponse.ok(companyInfoService.save(companyInfo));
	}
}
