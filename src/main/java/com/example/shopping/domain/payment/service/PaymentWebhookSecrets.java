package com.example.shopping.domain.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.shopping.domain.payment.entity.Payment;
import com.example.shopping.global.security.KeyedHash;
import com.example.shopping.global.security.SecretHashes;

/**
 * Toss가 가상계좌를 발급할 때 준 웹훅 검증 비밀값(secret)을 해시해 저장하고, 웹훅·PG 조회 결과의 비밀값과 비교한다.
 * 서버 키로 해시하므로(#136) DB가 새도 비밀값을 알아낼 수 없다.
 */
@Component
public class PaymentWebhookSecrets {

	private final KeyedHash hashes;

	@Autowired
	public PaymentWebhookSecrets(SecretHashes secretHashes) {
		this(secretHashes.forPurpose(SecretHashes.PAYMENT_WEBHOOK_SECRET));
	}

	PaymentWebhookSecrets(KeyedHash hashes) {
		this.hashes = hashes;
	}

	/** 저장할 해시. 비밀값이 없으면(카드 결제 등) null */
	public String hash(String secret) {
		return secret == null ? null : hashes.hash(secret);
	}

	/** 비밀값이 결제를 만들 때 받은 것과 같은지 */
	public boolean matches(Payment payment, String secret) {
		String stored = payment.getWebhookSecretHash();
		if (stored == null || secret == null) {
			return false;
		}
		return hashes.matches(stored, secret) || MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII),
				legacyHash(secret).getBytes(StandardCharsets.US_ASCII));
	}

	/**
	 * 서버 키 없이 SHA-256(Base64)으로 저장하던 이전 결제. 배포 때 입금을 기다리던 가상계좌도 웹훅을 확인할 수 있게 함께 비교한다.
	 * 가상계좌 입금 기한(최대 며칠)이 지나면 더 쓰이지 않으므로 그 뒤에 지워도 된다.
	 */
	private static String legacyHash(String secret) {
		try {
			return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
					.digest(secret.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}
	}
}
