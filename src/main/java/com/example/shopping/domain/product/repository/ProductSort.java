package com.example.shopping.domain.product.repository;

import java.util.Locale;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

/** 고객용 상품 목록의 정렬 기준. 요청에서는 ?sort=recommended처럼 소문자로 보낸다. */
public enum ProductSort {
	/** 추천순: 관리자가 정한 추천 점수가 높은 순 */
	RECOMMENDED,
	/** 가격 낮은순. 금액 미표시 상품은 항상 맨 뒤 */
	PRICE_ASC,
	/** 가격 높은순. 금액 미표시 상품은 항상 맨 뒤 */
	PRICE_DESC,
	/** 인기순: 누적 판매 수량이 많은 순 */
	POPULAR;

	/** 대소문자를 가리지 않는다. 값이 없으면 추천순. */
	public static ProductSort parse(String value) {
		if (value == null || value.isBlank()) {
			return RECOMMENDED;
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}
}
