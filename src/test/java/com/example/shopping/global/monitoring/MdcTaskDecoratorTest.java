package com.example.shopping.global.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void 요청_스레드의_요청_ID를_작업에_넘기고_작업이_끝나면_스레드에서_지운다() throws InterruptedException {
		AtomicReference<String> during = new AtomicReference<>();
		AtomicReference<String> after = new AtomicReference<>();
		MDC.put("requestId", "req-1");
		Runnable decorated = new MdcTaskDecorator().decorate(() -> during.set(MDC.get("requestId")));

		Thread worker = new Thread(() -> {
			decorated.run();
			after.set(MDC.get("requestId"));
		});
		worker.start();
		worker.join();

		assertThat(during.get()).isEqualTo("req-1");
		assertThat(after.get()).isNull();
	}
}
