package com.example.shopping.domain.legal.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.legal.dto.response.LegalDocumentResponse;
import com.example.shopping.domain.legal.entity.LegalDocument;
import com.example.shopping.domain.legal.entity.LegalDocumentType;
import com.example.shopping.domain.legal.repository.LegalDocumentRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 고객 화면의 약관·정책 문서. 공개한 버전만 보이고, 기본은 지금 시행 중인 버전이다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LegalDocumentService {

	private final LegalDocumentRepository legalDocumentRepository;

	/**
	 * version이 없으면 시행 중인 버전, 있으면 그 공개 버전(이전·시행 예정 버전 보기).
	 * 없는 종류, 공개하지 않은 버전, 시행 중인 버전이 없으면 404
	 */
	public LegalDocumentResponse get(String typeId, String version) {
		LegalDocumentType type = type(typeId);
		Optional<LegalDocument> document = version == null ? inForce(type)
				: legalDocumentRepository.findByDocumentTypeAndVersion(type, version)
						.filter(LegalDocument::isPublished);
		return document.map(LegalDocumentResponse::from)
				.orElseThrow(() -> new BusinessException(ErrorCode.LEGAL_DOCUMENT_NOT_FOUND));
	}

	/** 공개 버전 목록(시행 예정 포함). 시행일이 늦은 순 */
	public List<LegalDocumentResponse.Version> versions(String typeId) {
		LegalDocumentType type = type(typeId);
		Long currentId = inForce(type).map(LegalDocument::getId).orElse(null);
		return legalDocumentRepository.findByDocumentTypeAndPublishedTrueOrderByEffectiveAtDescIdDesc(type).stream()
				.map(document -> LegalDocumentResponse.Version.of(document, currentId))
				.toList();
	}

	/** 시행 중인 버전 이름. 동의 기록에 남긴다. 아직 없으면 빈 값 */
	public Optional<String> versionInForce(LegalDocumentType type) {
		return inForce(type).map(LegalDocument::getVersion);
	}

	private Optional<LegalDocument> inForce(LegalDocumentType type) {
		return legalDocumentRepository
				.findFirstByDocumentTypeAndPublishedTrueAndEffectiveAtLessThanEqualOrderByEffectiveAtDescIdDesc(type,
						LocalDate.now());
	}

	static LegalDocumentType type(String typeId) {
		return LegalDocumentType.fromId(typeId)
				.orElseThrow(() -> new BusinessException(ErrorCode.LEGAL_DOCUMENT_NOT_FOUND));
	}
}
