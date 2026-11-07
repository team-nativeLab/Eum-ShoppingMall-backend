package com.example.shopping.global.captcha;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * 로봇 확인(CAPTCHA) 설정. 서비스는 Cloudflare Turnstile을 쓴다.
 *
 * @param enabled          켜면 인증번호를 여러 번 틀린 요청에 로봇 확인 토큰을 요구한다. 프론트가 위젯을 붙인 뒤 켠다.
 * @param secretKey        Turnstile 위젯의 비밀 키. 환경변수로만 넣는다. 켰으면 반드시 있어야 한다.
 * @param allowedHostnames 위젯을 풀 수 있는 프론트 도메인(예: shop.example.com). 다른 사이트에서 푼 토큰은 받지 않는다.
 *                         켰으면 반드시 있어야 한다.
 * @param verifyUrl        Turnstile 토큰 확인 주소
 * @param timeout          확인 서비스 연결·응답 대기 시간
 */
@ConfigurationProperties("app.captcha")
public record CaptchaProperties(
		boolean enabled,
		String secretKey,
		List<String> allowedHostnames,
		@DefaultValue(CaptchaProperties.TURNSTILE_VERIFY_URL) String verifyUrl,
		@DefaultValue("3s") Duration timeout) {

	public static final String TURNSTILE_VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

	public CaptchaProperties {
		allowedHostnames = allowedHostnames == null ? List.of()
				: allowedHostnames.stream().filter(StringUtils::hasText)
						.map(hostname -> hostname.trim().toLowerCase(Locale.ROOT)).toList();
		if (enabled && !StringUtils.hasText(secretKey)) {
			throw new IllegalArgumentException("CAPTCHA_ENABLED=true이면 CAPTCHA_SECRET_KEY도 넣어야 합니다.");
		}
		if (enabled && allowedHostnames.isEmpty()) {
			throw new IllegalArgumentException("CAPTCHA_ENABLED=true이면 CAPTCHA_ALLOWED_HOSTNAMES도 넣어야 합니다.");
		}
		if (enabled && (verifyUrl == null || !verifyUrl.startsWith("https://"))) {
			throw new IllegalArgumentException("app.captcha.verify-url은 https 주소여야 합니다.");
		}
	}
}
