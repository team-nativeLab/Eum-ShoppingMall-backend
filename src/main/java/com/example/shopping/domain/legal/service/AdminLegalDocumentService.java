package com.example.shopping.domain.legal.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.legal.dto.request.LegalDocumentRequest;
import com.example.shopping.domain.legal.dto.response.LegalDocumentResponse;
import com.example.shopping.domain.legal.entity.LegalDocument;
import com.example.shopping.domain.legal.entity.LegalDocumentType;
import com.example.shopping.domain.legal.repository.LegalDocumentRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.validation.PlainText;

import lombok.RequiredArgsConstructor;

/** 관리자 약관·정책 문서 관리. 시행 중이거나 지난 공개 버전은 고치지 않고 새 버전으로 등록한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminLegalDocumentService {

	private final LegalDocumentRepository legalDocumentRepository;

	/** 종류별 시행일이 가장 늦은 버전(편집 화면의 기본값). 등록한 적이 없는 종류는 빠진다. */
	public List<LegalDocumentResponse.Admin> latest() {
		LocalDate today = LocalDate.now();
		return Arrays.stream(LegalDocumentType.values())
				.flatMap(type -> legalDocumentRepository.findByDocumentTypeOrderByEffectiveAtDescIdDesc(type).stream()
						.limit(1))
				.map(document -> LegalDocumentResponse.Admin.of(document, today))
				.toList();
	}

	/** 한 종류의 모든 버전(공개하지 않은 버전 포함). 시행일이 늦은 순 */
	public List<LegalDocumentResponse.Admin> versions(String typeId) {
		LocalDate today = LocalDate.now();
		return legalDocumentRepository.findByDocumentTypeOrderByEffectiveAtDescIdDesc(LegalDocumentService.type(typeId))
				.stream()
				.map(document -> LegalDocumentResponse.Admin.of(document, today))
				.toList();
	}

	/** 같은 버전이 있으면 고치고, 없으면 새로 만든다. 시행 중이거나 지난 공개 버전은 409 */
	@Transactional
	public LegalDocumentResponse.Admin save(String typeId, LegalDocumentRequest request) {
		LegalDocumentType type = LegalDocumentService.type(typeId);
		if (PlainText.containsHtmlTag(request.body())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		LocalDate today = LocalDate.now();
		String version = request.version().trim();
		String title = request.title().trim();
		LegalDocument document = legalDocumentRepository.findByDocumentTypeAndVersion(type, version)
				.map(existing -> {
					if (existing.isInForce(today)) {
						throw new BusinessException(ErrorCode.LEGAL_DOCUMENT_LOCKED);
					}
					existing.change(title, request.body(), request.effectiveAt(), request.published());
					return existing;
				})
				.orElseGet(() -> LegalDocument.create(type, version, title, request.body(), request.effectiveAt(),
						request.published()));
		// 같은 버전을 동시에 처음 저장하면 유니크 키 위반이 나고, 공통 처리(GlobalExceptionHandler)가 충돌로 답한다.
		return LegalDocumentResponse.Admin.of(legalDocumentRepository.saveAndFlush(document), today);
	}
}
