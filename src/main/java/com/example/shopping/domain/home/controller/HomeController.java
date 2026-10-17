package com.example.shopping.domain.home.controller;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.home.dto.AppConfigResponse;
import com.example.shopping.domain.home.dto.ContentResponse;
import com.example.shopping.domain.home.dto.HomeResponse;
import com.example.shopping.domain.home.service.HomeService;
import com.example.shopping.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@EnableConfigurationProperties(AppConfigResponse.class)
public class HomeController {

	private final HomeService homeService;
	private final AppConfigResponse appConfig;

	/** WEB-001, 002 메인 콘텐츠 (노출 중인 배너와 신상품) */
	@GetMapping("/home")
	public ApiResponse<HomeResponse> home() {
		return ApiResponse.ok(homeService.getHome());
	}

	/** WEB-025 회사소개. 내용은 site_content 테이블의 code = 'company' 행에서 읽는다. */
	@GetMapping("/contents/company")
	public ApiResponse<ContentResponse> company() {
		return ApiResponse.ok(homeService.getCompany());
	}

	/** 회사소개·약관 등 서버가 허용한 고정 콘텐츠. */
	@GetMapping("/contents/{code}")
	public ApiResponse<ContentResponse> content(@PathVariable String code) {
		return ApiResponse.ok(homeService.getContent(code));
	}

	/** APP-001 앱 구동 설정 (버전, 점검 여부) */
	@GetMapping("/app/config")
	public ApiResponse<AppConfigResponse> appConfig() {
		return ApiResponse.ok(appConfig);
	}
}
