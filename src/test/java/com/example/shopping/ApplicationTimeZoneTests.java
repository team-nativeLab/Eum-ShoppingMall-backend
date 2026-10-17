package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

class ApplicationTimeZoneTests {

	@Test
	void 서버_기본_시간대가_UTC여도_한국_시간으로_고정한다() {
		TimeZone originalTimeZone = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

			ShoppingApplication.configureBusinessTimeZone();

			assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Asia/Seoul"));
		} finally {
			TimeZone.setDefault(originalTimeZone);
		}
	}
}
