package com.example.shopping.domain.product.repository;

import java.util.Locale;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

/** 고객 상품 목록의 컬렉션(?collection=). 관리자가 상품마다 지정한다(#207). */
public enum ProductCollection {
	/** 베스트 상품(product.featured) */
	FEATURED,
	/** 오늘의 상품(product.todayDeal) */
	TODAY;

	/** 값이 없으면 컬렉션 조건 없음(null). featured·today 외의 값은 400 */
	public static ProductCollection parse(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "featured" -> FEATURED;
			case "today" -> TODAY;
			default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
		};
	}
}
