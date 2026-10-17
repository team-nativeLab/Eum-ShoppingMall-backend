package com.example.shopping.domain.upload.service;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "upload.storage", havingValue = "s3")
@EnableConfigurationProperties(S3UploadProperties.class)
public class S3StorageConfig {

	/**
	 * 업로드 요청이 S3 응답을 오래 기다리지 않도록 상한을 둔다 (시도 한 번 5초, 재시도 포함 10초).
	 * 접근 권한은 AWS 기본 방식(IAM 역할, 환경변수)으로 얻는다. 서버가 내려갈 때 스프링이 close()로 닫는다.
	 */
	@Bean
	S3Client s3Client(S3UploadProperties properties) {
		return S3Client.builder()
				.region(Region.of(properties.region()))
				.overrideConfiguration(config -> config
						.apiCallAttemptTimeout(Duration.ofSeconds(5))
						.apiCallTimeout(Duration.ofSeconds(10)))
				.build();
	}
}
