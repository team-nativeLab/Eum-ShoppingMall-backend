package com.example.shopping.domain.upload.dto.response;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

import com.example.shopping.domain.upload.service.PresignedUploadStorage.PresignedUpload;

/**
 * 클라이언트는 presignedUrl로 PUT 요청을 보내 파일을 올리고(헤더는 headers 그대로),
 * 성공하면 fileKey로 완료 요청(POST /api/v1/uploads/complete)을 보내 이미지 주소를 받는다.
 *
 * @param headers   업로드 요청에 그대로 넣어야 하는 헤더 (Content-Type, Content-Length, Cache-Control)
 * @param expiresAt 이 시각이 지나면 presignedUrl을 쓸 수 없다.
 */
public record PresignedUrlResponse(String presignedUrl, String fileKey, Map<String, String> headers,
		OffsetDateTime expiresAt) {

	public static PresignedUrlResponse from(PresignedUpload upload) {
		return new PresignedUrlResponse(upload.url(), upload.key(), upload.headers(),
				OffsetDateTime.ofInstant(upload.expiresAt(), ZoneId.systemDefault()));
	}
}
