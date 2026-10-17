package com.example.shopping.domain.upload.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * upload.storage=s3일 때만 읽는다. 값이 비어 있으면 서버가 뜨지 않는다.
 * 접근 권한은 키를 설정에 넣지 않고 AWS 기본 방식(IAM 역할, AWS_ACCESS_KEY_ID 등 환경변수)으로 얻는다.
 *
 * @param publicUrl 이미지 주소의 앞부분 (CDN 주소). 끝의 /는 없어도 된다.
 * @param keyPrefix 버킷 안에서 이미지를 둘 폴더
 */
@Validated
@ConfigurationProperties("upload.s3")
public record S3UploadProperties(
		@NotBlank(message = "UPLOAD_S3_BUCKET을 설정해 주세요.")
		String bucket,

		@NotBlank(message = "UPLOAD_S3_REGION을 설정해 주세요.")
		String region,

		@NotBlank(message = "UPLOAD_S3_PUBLIC_URL을 설정해 주세요.")
		@Pattern(regexp = "^https?://\\S+$", message = "UPLOAD_S3_PUBLIC_URL은 http(s) 주소여야 합니다.")
		String publicUrl,

		@NotBlank(message = "UPLOAD_S3_KEY_PREFIX를 설정해 주세요.")
		@Pattern(regexp = "^[a-z0-9-]+(/[a-z0-9-]+)*$", message = "UPLOAD_S3_KEY_PREFIX는 영문 소문자·숫자·하이픈 폴더 이름이어야 합니다.")
		String keyPrefix) {
}
