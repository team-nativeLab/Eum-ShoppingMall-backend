package com.example.shopping.domain.legal.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.legal.dto.request.LegalDocumentRequest;
import com.example.shopping.domain.legal.dto.response.LegalDocumentResponse;
import com.example.shopping.domain.legal.service.AdminLegalDocumentService;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 관리자 약관·정책 문서 관리. 버전마다 저장하며, 시행 중이거나 지난 공개 버전은 고칠 수 없다. */
@Tag(name = "약관 관리")
@RestController
@RequestMapping("/api/v1/admin/legal-documents")
@RequiredArgsConstructor
public class AdminLegalDocumentController {

	private final AdminLegalDocumentService adminLegalDocumentService;

	/** 종류별 시행일이 가장 늦은 버전. 등록한 적이 없는 종류는 빠진다. */
	@GetMapping
	public ApiResponse<List<LegalDocumentResponse.Admin>> documents() {
		return ApiResponse.ok(adminLegalDocumentService.latest());
	}

	/** 한 종류의 모든 버전(공개하지 않은 버전 포함) */
	@GetMapping("/{type}/versions")
	public ApiResponse<List<LegalDocumentResponse.Admin>> versions(@PathVariable String type) {
		return ApiResponse.ok(adminLegalDocumentService.versions(type));
	}

	/** 같은 버전이 있으면 고치고, 없으면 새 버전으로 만든다. 시행 중이거나 지난 공개 버전은 409(LEGAL_DOCUMENT_LOCKED) */
	@PutMapping("/{type}")
	public ApiResponse<LegalDocumentResponse.Admin> save(@PathVariable String type,
			@Valid @RequestBody LegalDocumentRequest request) {
		return ApiResponse.ok(adminLegalDocumentService.save(type, request));
	}
}
