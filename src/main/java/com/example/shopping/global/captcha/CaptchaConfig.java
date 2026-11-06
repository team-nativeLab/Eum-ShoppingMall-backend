package com.example.shopping.global.captcha;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaConfig {

	/** 확인 서비스가 늦어도 인증번호 확인 요청이 오래 붙잡히지 않도록 연결·응답 대기 시간을 제한한다. */
	@Bean
	CaptchaVerifier captchaVerifier(CaptchaProperties properties) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
		requestFactory.setReadTimeout(properties.timeout());
		return new SiteverifyCaptchaVerifier(RestClient.builder().requestFactory(requestFactory).build(), properties);
	}
}
