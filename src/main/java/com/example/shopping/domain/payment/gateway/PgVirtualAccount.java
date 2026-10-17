package com.example.shopping.domain.payment.gateway;

import java.time.LocalDateTime;

/** PG가 발급한 가상계좌 안내 정보. secret은 웹훅 검증에만 사용하고 API 응답에는 노출하지 않는다. */
public record PgVirtualAccount(String bankCode, String accountNumber, String customerName,
		LocalDateTime dueDate) {
}
