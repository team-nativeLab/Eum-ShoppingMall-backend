package com.example.shopping.domain.home.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.home.dto.RecommendedKeywords;
import com.example.shopping.domain.home.service.RecommendedKeywordService;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 관리자 추천 검색어 관리. 목록을 통째로 저장한다. */
@Tag(name = "추천 검색어 관리")
@RestController
@RequestMapping("/api/v1/admin/search/recommended-keywords")
@RequiredArgsConstructor
public class AdminRecommendedKeywordController {

	private final RecommendedKeywordService recommendedKeywordService;

	/** 저장한 추천 검색어. 저장한 적이 없으면 빈 목록 */
	@GetMapping
	public ApiResponse<RecommendedKeywords> get() {
		return ApiResponse.ok(recommendedKeywordService.get());
	}

	/** 추천 검색어 전체를 바꾼다. 앞뒤 공백을 지우고 겹치면 처음 것만 남긴다. */
	@PutMapping
	public ApiResponse<RecommendedKeywords> save(@Valid @RequestBody RecommendedKeywords keywords) {
		return ApiResponse.ok(recommendedKeywordService.save(keywords));
	}
}
