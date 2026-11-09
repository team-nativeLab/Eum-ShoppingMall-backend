package com.example.shopping.global.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * 관리자 로그인 보안 설정(#164). 「개인정보의 안전성 확보조치 기준」 제6조(접근통제)의 안전한 인증수단과 접속 자동 차단을 맞춘다.
 *
 * @param mfaRequired        관리자 로그인에 OTP(TOTP) 2단계 인증을 요구할지. 끄더라도 OTP를 등록한 관리자는 OTP를 확인한다.
 * @param mfaIssuer          OTP 앱에 보일 서비스 이름
 * @param sessionIdleTimeout 관리자가 이 시간 동안 재발급하지 않으면 다시 로그인해야 한다.
 * @param sessionMaxLifetime 로그인한 뒤 이 시간이 지나면 계속 쓰고 있어도 다시 로그인해야 한다.
 */
@Validated
@ConfigurationProperties("security.admin")
public record AdminSecurityProperties(
		@DefaultValue("true") boolean mfaRequired,
		@DefaultValue("Eum Shopping") @NotBlank String mfaIssuer,
		@DefaultValue("30m") Duration sessionIdleTimeout,
		@DefaultValue("8h") Duration sessionMaxLifetime) {

	public AdminSecurityProperties {
		if (sessionIdleTimeout.isNegative() || sessionIdleTimeout.isZero()
				|| sessionMaxLifetime.compareTo(sessionIdleTimeout) < 0) {
			throw new IllegalArgumentException("관리자 세션 시간은 0보다 크고, 최대 유지 시간은 미사용 제한 시간 이상이어야 합니다.");
		}
	}
}
