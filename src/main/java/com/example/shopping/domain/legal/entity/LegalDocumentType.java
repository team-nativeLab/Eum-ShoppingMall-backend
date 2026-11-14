package com.example.shopping.domain.legal.entity;

import java.util.Arrays;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 약관·정책 문서 종류. 주소와 응답에는 소문자 id(terms 등)를 쓴다. */
@Getter
@RequiredArgsConstructor
public enum LegalDocumentType {
	/** 이용약관 */
	TERMS("terms"),
	/** 개인정보 처리방침 */
	PRIVACY("privacy"),
	/** 이용안내 */
	GUIDE("guide"),
	/** 배송 안내 */
	SHIPPING("shipping");

	private final String id;

	public static Optional<LegalDocumentType> fromId(String id) {
		return Arrays.stream(values()).filter(type -> type.id.equals(id)).findFirst();
	}
}
