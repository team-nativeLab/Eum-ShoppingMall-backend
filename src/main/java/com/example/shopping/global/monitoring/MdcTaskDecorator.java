package com.example.shopping.global.monitoring;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/** 요청을 받은 스레드의 요청 ID를 백그라운드 작업에 넘겨, 그 작업의 로그도 같은 요청 ID로 찾을 수 있게 한다. */
public class MdcTaskDecorator implements TaskDecorator {

	@Override
	public Runnable decorate(Runnable task) {
		Map<String, String> context = MDC.getCopyOfContextMap();
		return () -> {
			if (context != null) {
				MDC.setContextMap(context);
			}
			try {
				task.run();
			} finally {
				MDC.clear();
			}
		};
	}
}
