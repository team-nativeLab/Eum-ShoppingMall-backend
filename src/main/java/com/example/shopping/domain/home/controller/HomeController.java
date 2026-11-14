package com.example.shopping.domain.home.controller;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.home.dto.CompanyInfo;
import com.example.shopping.domain.home.dto.response.AppConfigResponse;
import com.example.shopping.domain.home.dto.response.ContentResponse;
import com.example.shopping.domain.home.dto.response.HomeResponse;
import com.example.shopping.domain.home.service.CompanyInfoService;
import com.example.shopping.domain.home.service.HomeService;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "메인")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@EnableConfigurationProperties(AppConfigResponse.class)
public class HomeController {

	private final HomeService homeService;
	private final CompanyInfoService companyInfoService;
	private final AppConfigResponse appConfig;

	/** WEB-001, 002 메인 콘텐츠 (관리자 홈 편성, 노출 중인 배너, 신상품) */
	@GetMapping("/home")
	public ApiResponse<HomeResponse> home() {
		return ApiResponse.ok(homeService.getHome());
	}

	/** 푸터의 사업자·고객센터 정보. 관리자가 아직 저장하지 않았으면 404(CONTENT_NOT_FOUND) */
	@GetMapping("/company")
	public ApiResponse<CompanyInfo> company() {
		return ApiResponse.ok(companyInfoService.get());
	}

	/** WEB-025 회사소개(company)·이용약관(terms)·개인정보처리방침(privacy-policy) 등 서버가 허용한 고정 콘텐츠. */
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
