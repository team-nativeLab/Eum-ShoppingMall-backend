package com.example.shopping.global.captcha;

import java.util.Locale;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cloudflare Turnstile의 siteverify API(secret·response·remoteip를 폼으로 보내고 {"success", "hostname", "action"}을 받음)로
 * 토큰을 확인한다. success만 보지 않고, 허용한 사이트에서 이 용도로 푼 토큰인지도 확인한다(#146).
 */
@Slf4j
public class SiteverifyCaptchaVerifier implements CaptchaVerifier {

	private final RestClient restClient;
	private final CaptchaProperties properties;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public SiteverifyCaptchaVerifier(RestClient restClient, CaptchaProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public boolean isEnabled() {
		return properties.enabled();
	}

	@Override
	public boolean verify(String token, String action, String remoteIp) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("secret", properties.secretKey());
		form.add("response", token);
		if (StringUtils.hasText(remoteIp)) {
			form.add("remoteip", remoteIp);
		}
		String body;
		try {
			body = restClient.post()
					.uri(properties.verifyUrl())
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.body(String.class);
		} catch (RestClientException e) {
			log.warn("로봇 확인 서비스를 호출하지 못했습니다: {}", e.getClass().getSimpleName());
			throw new BusinessException(ErrorCode.CAPTCHA_UNAVAILABLE);
		}
		JsonNode result;
		try {
			result = jsonMapper.readTree(body == null ? "" : body);
		} catch (JacksonException e) {
			log.warn("로봇 확인 서비스 응답을 해석하지 못했습니다.");
			throw new BusinessException(ErrorCode.CAPTCHA_UNAVAILABLE);
		}
		JsonNode success = result.path("success");
		if (!success.isBoolean() || !success.booleanValue()) {
			log.info("로봇 확인에 실패했습니다: {}", result.path("error-codes"));
			return false;
		}
		// 위젯의 허용 도메인 설정에만 기대지 않는다. 다른 사이트나 다른 화면의 위젯에서 받은 토큰은 통과시키지 않는다.
		String hostname = text(result, "hostname").toLowerCase(Locale.ROOT);
		if (!properties.allowedHostnames().contains(hostname)) {
			log.warn("허용하지 않은 사이트에서 푼 로봇 확인 토큰입니다: hostname={}", hostname);
			return false;
		}
		String solvedAction = text(result, "action");
		if (!solvedAction.equals(action)) {
			log.warn("다른 용도의 위젯에서 받은 로봇 확인 토큰입니다: action={}, expected={}", solvedAction, action);
			return false;
		}
		return true;
	}

	private static String text(JsonNode result, String field) {
		JsonNode value = result.path(field);
		return value.isString() ? value.stringValue() : "";
	}
}
