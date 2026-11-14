package com.example.shopping.domain.member.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.member.dto.request.MemberAddressRequest;
import com.example.shopping.domain.member.dto.response.MemberAddressResponse;
import com.example.shopping.domain.member.service.MemberAddressService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.security.CurrentMember;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 마이페이지·주문서의 배송지 주소록. 로그인한 회원 본인의 배송지만 다룬다. */
@Tag(name = "배송지")
@RestController
@RequestMapping("/api/v1/me/addresses")
@RequiredArgsConstructor
public class MemberAddressController {

	private final MemberAddressService memberAddressService;

	/** 내 배송지 목록. 기본 배송지가 맨 앞이다. */
	@GetMapping
	public ApiResponse<List<MemberAddressResponse>> list(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.ok(memberAddressService.list(CurrentMember.id(jwt)));
	}

	/** 배송지 등록. 10개까지 저장할 수 있다. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MemberAddressResponse> create(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody MemberAddressRequest request) {
		return ApiResponse.ok(memberAddressService.create(CurrentMember.id(jwt), request));
	}

	/** 배송지 수정. isDefault가 true면 기본 배송지로 정한다. */
	@PutMapping("/{addressId}")
	public ApiResponse<MemberAddressResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long addressId,
			@Valid @RequestBody MemberAddressRequest request) {
		return ApiResponse.ok(memberAddressService.update(CurrentMember.id(jwt), addressId, request));
	}

	/** 배송지 삭제. 지난 주문의 배송지는 그대로 남는다. */
	@DeleteMapping("/{addressId}")
	public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long addressId) {
		memberAddressService.delete(CurrentMember.id(jwt), addressId);
		return ApiResponse.ok(null);
	}
}
