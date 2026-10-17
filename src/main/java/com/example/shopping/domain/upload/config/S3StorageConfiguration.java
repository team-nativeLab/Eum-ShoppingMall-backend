package com.example.shopping.domain.upload.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "upload.storage", havingValue = "s3")
public class S3StorageConfiguration {

	@Bean
	S3Client uploadS3Client(@Value("${upload.s3.region}") String region) {
		return S3Client.builder().region(Region.of(region)).build();
	}
}
