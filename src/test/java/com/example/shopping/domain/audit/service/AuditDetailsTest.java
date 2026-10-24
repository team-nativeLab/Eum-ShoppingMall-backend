package com.example.shopping.domain.audit.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class AuditDetailsTest {

	@Test
	void 바꾼_항목과_값을_남기고_비밀번호_같은_민감한_값은_가린다() {
		String details = AuditDetails.fromJsonBody(bytes("""
				{"name":"안전모","price":1000,"newPassword":"secret-1234",
				 "account":{"secretKey":"sk_live","holder":"홍길동"},
				 "skus":[{"optionName":"흰색","accessToken":"abc"}]}
				"""));

		assertThat(details)
				.contains("\"name\":\"안전모\"", "\"price\":1000", "\"holder\":\"홍길동\"", "\"optionName\":\"흰색\"")
				.contains("\"newPassword\":\"***\"", "\"secretKey\":\"***\"", "\"accessToken\":\"***\"")
				.doesNotContain("secret-1234", "sk_live", "abc");
	}

	@Test
	void JSON이_아니거나_비어_있으면_남기지_않는다() {
		assertThat(AuditDetails.fromJsonBody(null)).isNull();
		assertThat(AuditDetails.fromJsonBody(new byte[0])).isNull();
		assertThat(AuditDetails.fromJsonBody(bytes("not json"))).isNull();
	}

	@Test
	void 너무_길면_앞부분만_남긴다() {
		String details = AuditDetails.fromJsonBody(bytes("{\"description\":\"" + "가".repeat(5000) + "\"}"));

		assertThat(details).hasSize(AuditDetails.MAX_LENGTH + "...(생략)".length()).endsWith("...(생략)");
	}

	private byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}
}
