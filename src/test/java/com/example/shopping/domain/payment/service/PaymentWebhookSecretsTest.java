package com.example.shopping.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.example.shopping.domain.payment.entity.Payment;
import com.example.shopping.global.security.KeyedHash;
import com.example.shopping.global.security.SecretHashes;

/** 가상계좌 웹훅 비밀값을 서버 키로 해시하고, 이전 방식(SHA-256)으로 저장한 결제도 확인한다 (#136). */
class PaymentWebhookSecretsTest {

	private final PaymentWebhookSecrets secrets = new PaymentWebhookSecrets(
			KeyedHash.derive("test-secret-0123456789abcdef0123", SecretHashes.PAYMENT_WEBHOOK_SECRET));

	@Test
	void 서버_키로_해시해_저장하고_같은_비밀값만_맞다고_본다() throws Exception {
		String stored = secrets.hash("webhook-secret");

		assertThat(stored).hasSize(64).isNotEqualTo(legacyHash("webhook-secret"));
		assertThat(secrets.matches(payment(stored), "webhook-secret")).isTrue();
		assertThat(secrets.matches(payment(stored), "wrong-secret")).isFalse();
		assertThat(secrets.matches(payment(stored), null)).isFalse();
		assertThat(secrets.hash(null)).isNull();
	}

	@Test
	void 배포_전에_만든_입금_대기_가상계좌의_이전_해시도_확인한다() throws Exception {
		Payment legacy = payment(legacyHash("webhook-secret"));

		assertThat(secrets.matches(legacy, "webhook-secret")).isTrue();
		assertThat(secrets.matches(legacy, "wrong-secret")).isFalse();
		assertThat(secrets.matches(payment(null), "webhook-secret")).isFalse();
	}

	private static Payment payment(String webhookSecretHash) {
		Payment payment = mock(Payment.class);
		when(payment.getWebhookSecretHash()).thenReturn(webhookSecretHash);
		return payment;
	}

	/** #136 전에 저장하던 방식: 서버 키 없는 SHA-256(Base64) */
	private static String legacyHash(String secret) throws Exception {
		return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
				.digest(secret.getBytes(StandardCharsets.UTF_8)));
	}
}
