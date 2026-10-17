package com.example.shopping.domain.upload.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 업로드 이미지를 S3에 저장하고 CDN 주소를 돌려준다 (upload.storage=s3).
 * 버킷은 공개하지 않고 CDN으로만 내보내는 구성을 전제로 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "upload.storage", havingValue = "s3")
public class S3FileStorage implements FileStorage {

	// 파일 이름이 매번 새로 만든 UUID라 같은 주소의 내용은 바뀌지 않는다. 브라우저·CDN이 1년 동안 그대로 쓰게 한다.
	static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

	private static final Map<String, String> CONTENT_TYPES = Map.of(
			"jpg", "image/jpeg",
			"png", "image/png",
			"gif", "image/gif",
			"webp", "image/webp");

	private final S3Client s3Client;
	private final S3UploadProperties properties;
	private final String publicUrl;

	public S3FileStorage(S3Client s3Client, S3UploadProperties properties) {
		this.s3Client = s3Client;
		this.properties = properties;
		this.publicUrl = properties.publicUrl().replaceAll("/+$", "");
	}

	@Override
	public String store(byte[] content, String extension) {
		// 저장 위치는 서버가 정한다. 클라이언트가 보낸 파일 이름은 쓰지 않는다. (예: images/2026/10/{UUID}.png)
		LocalDate today = LocalDate.now();
		String key = "%s/%d/%02d/%s.%s".formatted(properties.keyPrefix(), today.getYear(), today.getMonthValue(),
				UUID.randomUUID(), extension);
		PutObjectRequest request = PutObjectRequest.builder()
				.bucket(properties.bucket())
				.key(key)
				.contentType(CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"))
				.cacheControl(CACHE_CONTROL)
				.build();
		try {
			s3Client.putObject(request, RequestBody.fromBytes(content));
		} catch (SdkException e) {
			log.error("S3에 이미지를 저장하지 못했습니다: bucket={}, key={}", properties.bucket(), key, e);
			throw new BusinessException(ErrorCode.UPLOAD_FAILED);
		}
		return publicUrl + "/" + key;
	}
}
