package com.example.shopping.global.monitoring;

import java.time.Duration;

import org.slf4j.helpers.MessageFormatter;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 사람이 확인해야 하는 상황(PG 금액 불일치, 자동 환불 실패, 입금과 주문 상태 충돌 등)을 오류 로그와
 * manual.check.required{reason} 지표로 남기고 담당자에게 메일로 알린다(#32).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManualCheckAlerts {

	// 해결되지 않으면 보정 작업이 같은 내용을 계속 올리므로 이 간격으로만 다시 알린다.
	private static final Duration REPEAT_AFTER = Duration.ofHours(6);

	private final MeterRegistry meterRegistry;
	private final OperatorAlerts operatorAlerts;

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
		String alert = MessageFormatter.arrayFormat("[수동 확인 필요: {}] " + message, withReason).getMessage();
		operatorAlerts.add(alert, alert, REPEAT_AFTER);
	}
}
