package com.example.shopping.domain.legal.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.legal.dto.response.LegalDocumentResponse;
import com.example.shopping.domain.legal.service.LegalDocumentService;
import com.example.shopping.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 약관·정책 문서(누구나). 종류는 terms(이용약관), privacy(개인정보 처리방침), guide(이용안내), shipping(배송 안내) */
@RestController
@RequestMapping("/api/v1/legal-documents")
@RequiredArgsConstructor
public class LegalDocumentController {

	private final LegalDocumentService legalDocumentService;

	/**
	 * 지금 시행 중인 공개 버전. version을 주면 그 공개 버전(이전·시행 예정 버전 보기).
	 * 없는 종류, 공개하지 않은 버전, 아직 시행 중인 버전이 없으면 404(LEGAL_DOCUMENT_NOT_FOUND)
	 */
	@GetMapping("/{type}")
	public ApiResponse<LegalDocumentResponse> document(@PathVariable String type,
			@RequestParam(required = false) String version) {
		return ApiResponse.ok(legalDocumentService.get(type, version));
	}

	/** 공개 버전 목록(시행 예정 포함, 본문 제외). 시행일이 늦은 순 */
	@GetMapping("/{type}/versions")
	public ApiResponse<List<LegalDocumentResponse.Version>> versions(@PathVariable String type) {
		return ApiResponse.ok(legalDocumentService.versions(type));
	}
}
