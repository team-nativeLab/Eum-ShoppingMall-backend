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
	 * 없는 컬럼이나 응답에 싣지 않는 컬럼으로 정렬하지 못하게 막는다. 페이지 위치도 함께 검사한다.
	 */
	public static void requireAllowed(Pageable pageable, Set<String> allowedProperties) {
		requirePageInRange(pageable);
		for (Sort.Order order : pageable.getSort()) {
			if (!allowedProperties.contains(order.getProperty())) {
				throw new BusinessException(ErrorCode.INVALID_INPUT);
			}
		}
	}

	/**
	 * ?page=에 아주 큰 값을 넣으면 건너뛸 행 수(page × size)가 DB 조회에 쓰는 int 범위를 넘어 500이 난다.
	 * 이런 위치에는 어차피 데이터가 있을 수 없으므로 400으로 거절한다.
	 */
	public static void requirePageInRange(Pageable pageable) {
		if (pageable.getOffset() > Integer.MAX_VALUE) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}
}
