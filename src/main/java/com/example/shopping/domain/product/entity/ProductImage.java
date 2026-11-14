package com.example.shopping.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품 상세 갤러리 이미지 한 장. 업로드 API가 돌려준 주소와 대체 문구 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage {

	@Column(nullable = false, length = 500)
	private String url;

	// 화면 낭독기가 읽는 대체 문구. 없으면 빈 문자열
	@Column(nullable = false, length = 200)
	private String alt;

	public ProductImage(String url, String alt) {
		this.url = url;
		this.alt = alt;
	}
}
