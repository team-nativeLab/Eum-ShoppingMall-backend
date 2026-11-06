package com.example.shopping.global.captcha;

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
 * siteverify 형식(secret·response·remoteip를 폼으로 보내고 {"success": true|false}를 받음)으로 토큰을 확인한다.
 * Cloudflare Turnstile, Google reCAPTCHA, hCaptcha가 모두 이 형식이라 확인 주소만 바꿔 쓸 수 있다.
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
	public boolean verify(String token, String remoteIp) {
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
		if (success.isBoolean() && success.booleanValue()) {
			return true;
		}
		log.info("로봇 확인에 실패했습니다: {}", result.path("error-codes"));
		return false;
	}
}
