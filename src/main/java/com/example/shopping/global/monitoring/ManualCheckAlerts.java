package com.example.shopping.global.monitoring;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 사람이 확인해야 하는 상황(PG 금액 불일치, 자동 환불 실패, 입금과 주문 상태 충돌 등)을 오류 로그와
 * manual.check.required{reason} 지표로 함께 남긴다. 지표에 알림을 걸면 담당자가 로그를 뒤지지 않아도 바로 안다(#32).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManualCheckAlerts {

	private final MeterRegistry meterRegistry;

	/**
	 * @param reason  지표 태그로 쓰는 짧은 영문 이름 (예: pg_amount_mismatch)
	 * @param message SLF4J 형식의 메시지. 마지막 인자가 예외면 스택도 남긴다.
	 */
	public void raise(String reason, String message, Object... args) {
		meterRegistry.counter("manual.check.required", "reason", reason).increment();
		Object[] withReason = new Object[args.length + 1];
		withReason[0] = reason;
		System.arraycopy(args, 0, withReason, 1, args.length);
		log.error("[수동 확인 필요: {}] " + message, withReason);
	}
}
