package com.example.shopping.domain.product.entity;

/** 상품 설명(description)의 서식. 화면은 어느 쪽이든 텍스트로만 그린다(HTML을 해석하지 않는다). */
public enum ProductDescriptionFormat {
	/** 평문. 줄바꿈만 살린다. */
	PLAIN,
	/** 제한된 서식(rich-v1): **굵게**, *기울임*, ++밑줄++, [링크](https://…), "- " 목록 */
	RICH_V1
}
