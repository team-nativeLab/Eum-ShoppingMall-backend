package com.example.shopping.domain.event.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.event.dto.request.ShopEventRequest;
import com.example.shopping.domain.event.dto.response.ShopEventResponse;
import com.example.shopping.domain.event.service.AdminShopEventService;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 관리자 이벤트·기획전 관리 */
@Tag(name = "이벤트 관리")
@RestController
@RequestMapping("/api/v1/admin/events")
@RequiredArgsConstructor
public class AdminShopEventController {

	private final AdminShopEventService adminShopEventService;

	/** 숨긴 이벤트를 포함한 전체 목록 */
	@GetMapping
	public ApiResponse<List<ShopEventResponse.Admin>> events() {
		return ApiResponse.ok(adminShopEventService.list());
	}

	/** 이벤트 등록. 종료 시각이 시작 시각보다 앞서면 400(INVALID_EVENT_PERIOD) */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<ShopEventResponse.Admin> create(@Valid @RequestBody ShopEventRequest request) {
		return ApiResponse.ok(adminShopEventService.create(request));
	}

	/** 전체 수정. 본문 구성도 통째로 바꾼다. */
	@PutMapping("/{eventId}")
	public ApiResponse<ShopEventResponse.Admin> update(@PathVariable Long eventId,
			@Valid @RequestBody ShopEventRequest request) {
		return ApiResponse.ok(adminShopEventService.update(eventId, request));
	}

	/** 이벤트 삭제(본문 구성도 함께 지운다) */
	@DeleteMapping("/{eventId}")
	public ApiResponse<Void> delete(@PathVariable Long eventId) {
		adminShopEventService.delete(eventId);
		return ApiResponse.ok(null);
	}
}
