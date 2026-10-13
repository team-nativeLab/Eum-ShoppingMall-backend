package com.example.shopping.domain.payment.gateway;

import lombok.Getter;

/** PG가 요청을 명확히 거절했다 (한도 초과, 잘못된 결제키, 이미 취소된 결제 등). */
@Getter
public class PgRejectedException extends RuntimeException {

	private final String code;

	public PgRejectedException(String code, Throwable cause) {
		super("PG 거절: " + code, cause);
		this.code = code;
	}
}
