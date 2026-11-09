package com.example.shopping.global.security;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 TOTP(HMAC-SHA1, 30초, 6자리). Google OTP·Microsoft Authenticator 같은 OTP 앱의 기본값이다(#164).
 * 외부 라이브러리 없이 표준 HMAC으로 계산한다.
 */
public final class Totp {

	/** 비밀 키 길이(160비트). RFC 4226이 권하는 길이다. */
	public static final int SECRET_BYTES = 20;
	static final long STEP_SECONDS = 30;

	private static final String ALGORITHM = "HmacSHA1";
	private static final Pattern CODE = Pattern.compile("[0-9]{6}");
	private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

	private Totp() {
	}

	/** 시각이 속한 30초 칸의 번호 */
	public static long step(Instant now) {
		return Math.floorDiv(now.getEpochSecond(), STEP_SECONDS);
	}

	/** 그 칸의 6자리 코드(앞자리 0 포함) */
	public static String code(byte[] secret, long step) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(secret, ALGORITHM));
			byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
			int offset = hash[hash.length - 1] & 0x0f;
			int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
					| ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
			return String.format("%06d", binary % 1_000_000);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 코드가 맞는 칸의 번호. 휴대폰 시계가 조금 어긋나도 되도록 앞뒤 한 칸(30초)까지 받는다.
	 * 비교 시간으로 맞은 자릿수를 알아내지 못하게 세 칸을 모두 같은 방법으로 비교한다.
	 */
	public static OptionalLong matchingStep(byte[] secret, String code, Instant now) {
		if (code == null || !CODE.matcher(code).matches()) {
			return OptionalLong.empty();
		}
		long current = step(now);
		long matched = Long.MIN_VALUE;
		for (long candidate = current - 1; candidate <= current + 1; candidate++) {
			if (MessageDigest.isEqual(code(secret, candidate).getBytes(StandardCharsets.US_ASCII),
					code.getBytes(StandardCharsets.US_ASCII))) {
				matched = candidate;
			}
		}
		return matched == Long.MIN_VALUE ? OptionalLong.empty() : OptionalLong.of(matched);
	}

	/** OTP 앱에 직접 입력하는 비밀 키 문자열(RFC 4648 Base32, 패딩 없음) */
	public static String base32(byte[] bytes) {
		StringBuilder result = new StringBuilder((bytes.length * 8 + 4) / 5);
		int buffer = 0;
		int bits = 0;
		for (byte value : bytes) {
			buffer = (buffer << 8) | (value & 0xff);
			bits += 8;
			while (bits >= 5) {
				result.append(BASE32[(buffer >>> (bits - 5)) & 0x1f]);
				bits -= 5;
			}
		}
		if (bits > 0) {
			result.append(BASE32[(buffer << (5 - bits)) & 0x1f]);
		}
		return result.toString();
	}

	/** OTP 앱이 QR 코드로 읽는 등록 주소(Key Uri Format) */
	public static String otpauthUri(String issuer, String account, byte[] secret) {
		return "otpauth://totp/" + encode(issuer) + ":" + encode(account)
				+ "?secret=" + base32(secret)
				+ "&issuer=" + encode(issuer)
				+ "&algorithm=SHA1&digits=6&period=" + STEP_SECONDS;
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}
}
