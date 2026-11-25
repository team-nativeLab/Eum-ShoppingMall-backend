package com.example.shopping.domain.home.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.home.dto.response.PopupResponse;
import com.example.shopping.domain.home.service.PopupService;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 웹 홈 첫 화면에 띄우는 팝업(누구나). "다시 보지 않기"는 웹이 브라우저에 기억한다. */
@Tag(name = "팝업")
@RestController
@RequestMapping("/api/v1/popups")
@RequiredArgsConstructor
public class PopupController {

	private final PopupService popupService;

	/** 노출 중인 팝업. 정렬 순서대로, 같으면 최근 등록한 순. 없으면 빈 목록 */
	@GetMapping
	public ApiResponse<List<PopupResponse>> popups() {
		return ApiResponse.ok(popupService.visible());
	}
}
