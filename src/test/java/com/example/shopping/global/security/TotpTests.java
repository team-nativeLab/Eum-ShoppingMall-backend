package com.example.shopping.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** 관리자 OTP(TOTP, #164). OTP 앱과 같은 값을 내는지 RFC 예시 값으로 확인한다. */
class TotpTests {

	/** RFC 6238 부록 B의 SHA-1 비밀 키 */
	private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

	@Test
	void RFC_6238_예시_값과_같은_코드를_낸다() {
		// 부록 B의 8자리 값에서 끝 6자리
		assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(59)))).isEqualTo("287082");
		assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
		assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1111111111)))).isEqualTo("050471");
		assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1234567890)))).isEqualTo("005924");
		assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(2000000000)))).isEqualTo("279037");
	}

	@Test
	void 휴대폰_시계가_앞뒤로_30초까지_어긋나도_받고_그보다_멀면_받지_않는다() {
		Instant now = Instant.ofEpochSecond(1234567890);
		long current = Totp.step(now);

		assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, current), now)).hasValue(current);
		assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, current - 1), now)).hasValue(current - 1);
		assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, current + 1), now)).hasValue(current + 1);
		assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, current - 2), now)).isEmpty();
		assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, current + 2), now)).isEmpty();
	}

	@Test
	void 숫자_6자리가_아니면_비교하지_않는다() {
		Instant now = Instant.ofEpochSecond(1234567890);
		String code = Totp.code(RFC_SECRET, Totp.step(now));

		assertThat(Totp.matchingStep(RFC_SECRET, null, now)).isEmpty();
		assertThat(Totp.matchingStep(RFC_SECRET, " " + code, now)).isEmpty();
		assertThat(Totp.matchingStep(RFC_SECRET, code.substring(1), now)).isEmpty();
		assertThat(Totp.matchingStep(RFC_SECRET, "０" + code.substring(1), now)).isEmpty();
	}

	@Test
	void 비밀_키는_Base32로_보여_주고_QR_주소에_서비스_이름과_계정을_넣는다() {
		// RFC 4648 예시 값
		assertThat(Totp.base32("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
		assertThat(Totp.base32(RFC_SECRET)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");

		assertThat(Totp.otpauthUri("Eum Shopping", "admin@example.com", RFC_SECRET)).isEqualTo(
				"otpauth://totp/Eum%20Shopping:admin%40example.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
						+ "&issuer=Eum%20Shopping&algorithm=SHA1&digits=6&period=30");
	}
}
