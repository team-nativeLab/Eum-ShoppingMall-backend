package com.example.shopping.domain.event.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.event.dto.response.ShopEventResponse;
import com.example.shopping.domain.event.service.ShopEventService;
import com.example.shopping.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 이벤트·기획전 목록·상세(누구나). 숨긴 이벤트는 목록에서 빠지고 상세는 404다. */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class ShopEventController {

	private final ShopEventService shopEventService;

	/** 보이는 이벤트. 정렬 순서, 같으면 최근 시작한 순. 끝난 이벤트도 상태(ENDED)를 달아 포함한다. */
	@GetMapping
	public ApiResponse<List<ShopEventResponse>> events() {
		return ApiResponse.ok(shopEventService.list());
	}

	@GetMapping("/{eventId}")
	public ApiResponse<ShopEventResponse> event(@PathVariable Long eventId) {
		return ApiResponse.ok(shopEventService.get(eventId));
	}
}
