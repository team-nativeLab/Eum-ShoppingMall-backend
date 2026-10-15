package com.example.shopping.domain.product.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 상품이 받은 안전 인증의 종류. 한 상품이 여러 인증을 가질 수 있다. 요청·응답에는 이름(KCS 등)을 쓴다. */
@Getter
@RequiredArgsConstructor
public enum Certification {
	/** 산업안전보건법의 안전인증 */
	KCS("KCs 안전인증"),
	/** 산업안전보건법의 자율안전확인신고 */
	SELF_CONFIRMATION("자율안전확인"),
	/** 소방용품 형식승인 */
	TYPE_APPROVAL("형식승인");

	/** 화면에 보여 줄 이름 */
	private final String label;
}
