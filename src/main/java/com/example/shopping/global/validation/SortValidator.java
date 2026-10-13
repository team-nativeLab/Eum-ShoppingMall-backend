package com.example.shopping.global.validation;

import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

public final class SortValidator {

	private SortValidator() {
	}

	/**
	 * 요청의 ?sort= 값이 허용한 기준에만 해당하는지 확인한다.
	 * 없는 컬럼이나 응답에 싣지 않는 컬럼으로 정렬하지 못하게 막는다.
	 */
	public static void requireAllowed(Pageable pageable, Set<String> allowedProperties) {
		for (Sort.Order order : pageable.getSort()) {
			if (!allowedProperties.contains(order.getProperty())) {
				throw new BusinessException(ErrorCode.INVALID_INPUT);
			}
		}
	}
}
