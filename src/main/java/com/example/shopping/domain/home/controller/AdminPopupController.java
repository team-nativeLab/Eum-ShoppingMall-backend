package com.example.shopping.domain.home.controller;

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

import com.example.shopping.domain.home.dto.request.PopupRequest;
import com.example.shopping.domain.home.dto.response.PopupResponse;
import com.example.shopping.domain.home.service.AdminPopupService;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 웹 홈 첫 화면 팝업 관리(#211). 사진은 업로드 API가 돌려준 주소나 웹의 정적 이미지(/assets/...)만 받는다. */
@Tag(name = "팝업 관리")
@RestController
@RequestMapping("/api/v1/admin/popups")
@RequiredArgsConstructor
public class AdminPopupController {

	private final AdminPopupService adminPopupService;

	/** 팝업 목록(숨긴 팝업 포함). 정렬 순서대로, 같으면 최근 등록한 순 */
	@GetMapping
	public ApiResponse<List<PopupResponse.Admin>> popups() {
		return ApiResponse.ok(adminPopupService.list());
	}

	/** 팝업 등록. 제목·내용은 필수이고 사진은 없어도 된다. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<PopupResponse.Admin> create(@Valid @RequestBody PopupRequest request) {
		return ApiResponse.ok(adminPopupService.create(request));
	}

	/**
	 * 팝업 전체 수정. 사진을 지우려면 imageUrl을 null이나 빈 문자열로 보낸다.
	 *
	 * @param popupId 팝업 ID
	 */
	@PutMapping("/{popupId}")
	public ApiResponse<PopupResponse.Admin> update(@PathVariable Long popupId,
			@Valid @RequestBody PopupRequest request) {
		return ApiResponse.ok(adminPopupService.update(popupId, request));
	}

	/**
	 * 팝업 삭제
	 *
	 * @param popupId 팝업 ID
	 */
	@DeleteMapping("/{popupId}")
	public ApiResponse<Void> delete(@PathVariable Long popupId) {
		adminPopupService.delete(popupId);
		return ApiResponse.ok(null);
	}
}
