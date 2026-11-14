package com.example.shopping.domain.legal.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.legal.entity.LegalDocument;
import com.example.shopping.domain.legal.entity.LegalDocumentType;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, Long> {

	Optional<LegalDocument> findByDocumentTypeAndVersion(LegalDocumentType documentType, String version);

	/** 시행 중인 버전: 시행일이 오늘이거나 지난 공개 버전 중 시행일이 가장 늦은 것 */
	Optional<LegalDocument> findFirstByDocumentTypeAndPublishedTrueAndEffectiveAtLessThanEqualOrderByEffectiveAtDescIdDesc(
			LegalDocumentType documentType, LocalDate today);

	/** 공개 버전. 시행 예정 버전도 포함하며, 시행일이 늦은 순 */
	List<LegalDocument> findByDocumentTypeAndPublishedTrueOrderByEffectiveAtDescIdDesc(LegalDocumentType documentType);

	/** 관리자용 전체 버전. 시행일이 늦은 순 */
	List<LegalDocument> findByDocumentTypeOrderByEffectiveAtDescIdDesc(LegalDocumentType documentType);
}
