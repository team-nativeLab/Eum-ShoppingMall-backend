package com.example.shopping.domain.payment.gateway;

/** PG 응답을 받지 못해 결과를 알 수 없다 (타임아웃, 연결 실패, PG 장애). 실패로 단정하면 안 된다. */
public class PgUnavailableException extends RuntimeException {

	public PgUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
