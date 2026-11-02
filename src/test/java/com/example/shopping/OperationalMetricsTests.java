package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import io.micrometer.core.instrument.MeterRegistry;

/** 담당자 알림 기준(OperationalMetricAlerts)이 읽는 지표가 실제 앱에서 그 이름으로 쌓이는지 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperationalMetricsTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MeterRegistry meters;

	@Test
	void HTTP_응답과_DB_커넥션_지표가_쌓인다() throws Exception {
		mockMvc.perform(get("/api/v1/app/config")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());

		assertThat(meters.find("http.server.requests").tag("status", "200").timers()).isNotEmpty();
		// 헬스 체크를 5xx 비율에서 뺄 때 이 경로 이름을 본다.
		assertThat(meters.find("http.server.requests").timers())
				.anyMatch(timer -> String.valueOf(timer.getId().getTag("uri")).startsWith("/actuator"));
		assertThat(meters.find("hikaricp.connections.timeout").counters()).isNotEmpty();
		assertThat(meters.find("hikaricp.connections.acquire").timers()).isNotEmpty();
	}
}
