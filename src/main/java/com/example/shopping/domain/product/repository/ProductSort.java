package com.example.shopping.domain.product.repository;

import java.util.Locale;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

/**
 * 고객용 상품 목록의 정렬 기준. 요청에서는 ?sort=price_asc처럼 보낸다.
 * 대소문자와 밑줄은 가리지 않으므로 priceAsc, PRICE_ASC도 같은 뜻이다.
 */
public enum ProductSort {
	/** 추천순: 관리자가 정한 추천 점수가 높은 순 */
	RECOMMENDED,
	/** 최신순: 최근에 등록한 순 */
	NEWEST,
	/** 가격 낮은순. 금액 미표시 상품은 항상 맨 뒤 */
	PRICE_ASC,
	/** 가격 높은순. 금액 미표시 상품은 항상 맨 뒤 */
	PRICE_DESC,
	/** 인기순: 누적 판매 수량이 많은 순 */
	POPULAR;

	/** 값이 없으면 추천순. */
	public static ProductSort parse(String value) {
		if (value == null || value.isBlank()) {
			return RECOMMENDED;
		}
		String normalized = normalize(value);
		for (ProductSort sort : values()) {
			if (normalize(sort.name()).equals(normalized)) {
				return sort;
			}
		}
		throw new BusinessException(ErrorCode.INVALID_INPUT);
	}

	private static String normalize(String value) {
		return value.trim().replace("_", "").toUpperCase(Locale.ROOT);
	}
}
