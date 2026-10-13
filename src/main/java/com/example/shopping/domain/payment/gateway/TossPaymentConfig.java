package com.example.shopping.domain.payment.gateway;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class TossPaymentConfig {

	/** 응답이 늦는 PG 때문에 요청이 무한정 붙잡히지 않도록 연결·응답 대기 시간을 제한한다. */
	@Bean
	PaymentGateway paymentGateway(TossProperties properties) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
		requestFactory.setReadTimeout(Duration.ofSeconds(10));

		RestClient restClient = RestClient.builder()
				.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory)
				.build();
		return new TossPaymentGateway(restClient, properties);
	}
}
