package com.example.shopping.domain.upload.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 업로드 전용 IAM 사용자의 액세스 키로 S3에 접근한다. 키는 환경변수로만 받고 설정 파일에 적지 않는다.
 * 키가 비어 있으면 서버가 뜨지 않는다.
 */
@Configuration
@ConditionalOnProperty(name = "upload.storage", havingValue = "s3")
public class S3StorageConfiguration {

	@Bean
	AwsCredentialsProvider uploadS3Credentials(@Value("${upload.s3.access-key}") String accessKey,
			@Value("${upload.s3.secret-key}") String secretKey) {
		Assert.hasText(accessKey, "UPLOAD_S3_ACCESS_KEY is required when UPLOAD_STORAGE=s3");
		Assert.hasText(secretKey, "UPLOAD_S3_SECRET_KEY is required when UPLOAD_STORAGE=s3");
		return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
	}

	/** 업로드 요청이 S3 응답을 오래 기다리지 않도록 상한을 둔다 (시도 한 번 5초, 재시도 포함 10초). */
	@Bean
	S3Client uploadS3Client(@Value("${upload.s3.region}") String region, AwsCredentialsProvider uploadS3Credentials) {
		return S3Client.builder()
				.region(Region.of(region))
				.credentialsProvider(uploadS3Credentials)
				.overrideConfiguration(config -> config
						.apiCallAttemptTimeout(Duration.ofSeconds(5))
						.apiCallTimeout(Duration.ofSeconds(10)))
				.build();
	}

	/** 직접 업로드 주소(Presigned URL)에 서명한다. S3를 호출하지 않고 서버 안에서 계산만 한다. */
	@Bean
	S3Presigner uploadS3Presigner(@Value("${upload.s3.region}") String region,
			AwsCredentialsProvider uploadS3Credentials) {
		return S3Presigner.builder().region(Region.of(region)).credentialsProvider(uploadS3Credentials).build();
	}
}
