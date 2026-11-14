package com.example.shopping.domain.legal.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.shopping.domain.legal.entity.LegalDocument;

/**
 * 고객 화면의 약관·정책 문서.
 *
 * @param type        문서 종류(terms, privacy, guide, shipping)
 * @param effectiveAt 시행일
 */
public record LegalDocumentResponse(String type, String version, String title, LocalDate effectiveAt, String body) {

	public static LegalDocumentResponse from(LegalDocument document) {
		return new LegalDocumentResponse(document.getDocumentType().getId(), document.getVersion(),
				document.getTitle(), document.getEffectiveAt(), document.getBody());
	}

	/**
	 * 공개 버전 목록의 한 줄(본문 제외).
	 *
	 * @param current 지금 시행 중인 버전인지. 시행일이 오늘보다 뒤면 시행 예정이다.
	 */
	public record Version(String version, String title, LocalDate effectiveAt, boolean current) {

		public static Version of(LegalDocument document, Long currentId) {
			return new Version(document.getVersion(), document.getTitle(), document.getEffectiveAt(),
					document.getId().equals(currentId));
		}
	}

	/**
	 * 관리자 화면의 버전.
	 *
	 * @param locked 시행 중이거나 지난 공개 버전이라 고칠 수 없는지. 바꾸려면 새 버전으로 등록한다.
	 */
	public record Admin(Long id, String type, String version, String title, LocalDate effectiveAt, String body,
			boolean published, boolean locked, LocalDateTime updatedAt) {

		public static Admin of(LegalDocument document, LocalDate today) {
			return new Admin(document.getId(), document.getDocumentType().getId(), document.getVersion(),
					document.getTitle(), document.getEffectiveAt(), document.getBody(), document.isPublished(),
					document.isInForce(today), document.getUpdatedAt());
		}
	}
}
