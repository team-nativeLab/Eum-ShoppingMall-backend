package com.example.shopping.domain.product.controller;

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

import com.example.shopping.domain.product.dto.request.SupplierRequest;
import com.example.shopping.domain.product.dto.response.SupplierResponse;
import com.example.shopping.domain.product.service.AdminSupplierService;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 발주처(상품을 받아 오는 거래처) 관리(#208). 담당자 연락처가 보이는 목록 조회는 감사 로그에 남는다. */
@Tag(name = "발주처 관리")
@RestController
@RequestMapping("/api/v1/admin/suppliers")
@RequiredArgsConstructor
public class AdminSupplierController {

	private final AdminSupplierService adminSupplierService;

	/** 발주처 목록. 최근 등록한 순 */
	@GetMapping
	public ApiResponse<List<SupplierResponse>> suppliers() {
		return ApiResponse.ok(adminSupplierService.list());
	}

	/** 발주처 등록. 주문 수신 방식이 EMAIL이면 orderEmail, FAX면 orderFax가 필요하다(SUPPLIER_ORDER_CONTACT_REQUIRED). */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
		return ApiResponse.ok(adminSupplierService.create(request));
	}

	/** 발주처 전체 수정 */
	@PutMapping("/{supplierId}")
	public ApiResponse<SupplierResponse> update(@PathVariable Long supplierId,
			@Valid @RequestBody SupplierRequest request) {
		return ApiResponse.ok(adminSupplierService.update(supplierId, request));
	}

	/** 발주처 삭제. 상품에 연결된 발주처는 409(SUPPLIER_IN_USE) */
	@DeleteMapping("/{supplierId}")
	public ApiResponse<Void> delete(@PathVariable Long supplierId) {
		adminSupplierService.delete(supplierId);
		return ApiResponse.ok(null);
	}
}
