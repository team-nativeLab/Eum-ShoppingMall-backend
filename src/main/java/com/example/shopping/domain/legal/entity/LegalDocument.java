package com.example.shopping.domain.legal.entity;

import java.time.LocalDate;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 약관·정책 문서의 한 버전. 시행일이 오늘이거나 지난 공개 버전(isInForce)은 고치지 않고 새 버전으로 등록해,
 * 고객이 동의했던 내용과 이전 처리방침을 그대로 보관한다.
 */
@Entity
@Table(name = "legal_document", uniqueConstraints = @UniqueConstraint(name = "uk_legal_document_type_version",
		columnNames = { "document_type", "version" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalDocument extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LegalDocumentType documentType;

	// 운영자가 정하는 버전 이름(예: 2.0). 종류 안에서 겹치지 않는다.
	@Column(nullable = false, length = 30)
	private String version;

	@Column(nullable = false, length = 200)
	private String title;

	// 평문. 화면은 텍스트로만 그린다.
	@Column(nullable = false, columnDefinition = "MEDIUMTEXT")
	private String body;

	// 시행일. 이날 0시(서버 시간대)부터 시행한다.
	@Column(nullable = false)
	private LocalDate effectiveAt;

	@Column(nullable = false)
	private boolean published;

	public static LegalDocument create(LegalDocumentType documentType, String version, String title, String body,
			LocalDate effectiveAt, boolean published) {
		LegalDocument document = new LegalDocument();
		document.documentType = documentType;
		document.version = version;
		document.change(title, body, effectiveAt, published);
		return document;
	}

	public void change(String title, String body, LocalDate effectiveAt, boolean published) {
		this.title = title;
		this.body = body;
		this.effectiveAt = effectiveAt;
		this.published = published;
	}

	/** 공개했고 시행일이 오늘이거나 지났는지. 이런 버전은 고치지 않는다. */
	public boolean isInForce(LocalDate today) {
		return published && !effectiveAt.isAfter(today);
	}
}
