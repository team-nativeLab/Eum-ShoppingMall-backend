package com.example.shopping.domain.upload.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 접근 권한은 키를 설정에 넣지 않고 AWS 기본 방식(ECS 태스크 역할, 인스턴스 프로파일 등)으로 얻는다. */
@Configuration
@ConditionalOnProperty(name = "upload.storage", havingValue = "s3")
public class S3StorageConfiguration {

	/** 업로드 요청이 S3 응답을 오래 기다리지 않도록 상한을 둔다 (시도 한 번 5초, 재시도 포함 10초). */
	@Bean
	S3Client uploadS3Client(@Value("${upload.s3.region}") String region) {
		return S3Client.builder()
				.region(Region.of(region))
				.overrideConfiguration(config -> config
						.apiCallAttemptTimeout(Duration.ofSeconds(5))
						.apiCallTimeout(Duration.ofSeconds(10)))
				.build();
	}

	/** 직접 업로드 주소(Presigned URL)에 서명한다. S3를 호출하지 않고 서버 안에서 계산만 한다. */
	@Bean
	S3Presigner uploadS3Presigner(@Value("${upload.s3.region}") String region) {
		return S3Presigner.builder().region(Region.of(region)).build();
	}
}
