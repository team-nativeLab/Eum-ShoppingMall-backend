package com.example.shopping.domain.product.repository;

import java.util.Locale;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

/** 고객 상품 목록의 컬렉션(?collection=). 베스트·오늘의 상품은 관리자가 상품마다 지정하고(#207), 할인 상품은 가격으로 정한다(#213). */
public enum ProductCollection {
	/** 베스트 상품(product.featured) */
	FEATURED,
	/** 오늘의 상품(product.todayDeal) */
	TODAY,
	/** 할인 상품(#213). 금액을 보여 주는 상품 가운데 정가(listPrice)가 판매가(basePrice)보다 높은 상품 */
	DISCOUNTED;

	/** 값이 없으면 컬렉션 조건 없음(null). featured·today·discounted 외의 값은 400 */
	public static ProductCollection parse(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "featured" -> FEATURED;
			case "today" -> TODAY;
			case "discounted" -> DISCOUNTED;
			default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
		};
	}
}
