package com.example.shopping.domain.home.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.home.service.RecommendedKeywordService;
import com.example.shopping.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 검색 결과가 없을 때 보여 줄 추천 검색어(누구나) */
@RestController
@RequestMapping("/api/v1/search/recommended-keywords")
@RequiredArgsConstructor
public class RecommendedKeywordController {

	private final RecommendedKeywordService recommendedKeywordService;

	/** 운영자가 정한 순서대로. 정하지 않았으면 빈 배열 */
	@GetMapping
	public ApiResponse<List<String>> keywords() {
		return ApiResponse.ok(recommendedKeywordService.getKeywords());
	}
}
