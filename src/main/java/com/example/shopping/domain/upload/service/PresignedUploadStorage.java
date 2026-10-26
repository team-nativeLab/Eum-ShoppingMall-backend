package com.example.shopping.domain.upload.service;

import java.time.Instant;
import java.util.Map;

/**
 * 클라이언트가 서버를 거치지 않고 저장소에 직접 올리는 업로드(Presigned URL). S3에서만 쓸 수 있다.
 * 직접 올린 파일은 서버가 내용을 보지 못했으므로, 쓰기 전에 inspect로 확인한다.
 */
public interface PresignedUploadStorage {

	/**
	 * 정해진 경로·형식·크기로만 올릴 수 있는 주소를 만든다.
	 *
	 * @param extension 점을 뺀 확장자 (예: png). 저장 경로를 만들 때 쓴다.
	 */
	PresignedUpload presign(String extension, String contentType, long contentLength);

	/**
	 * 직접 올린 파일의 크기와 앞부분(최대 headBytes)을 읽는다.
	 * 이 서버가 발급한 경로 형식이 아니거나 파일이 없으면 UPLOAD_NOT_FOUND.
	 */
	UploadedObject inspect(String key, int headBytes);

	/** 확인에 실패한 파일을 지운다. 이 서버가 발급한 경로 형식이 아니면 아무것도 하지 않는다. */
	void delete(String key);

	/** 저장된 파일에 접근할 주소 (CDN 주소) */
	String publicUrl(String key);

	/**
	 * @param url       PUT으로 파일을 올릴 주소
	 * @param key       저장 경로. 업로드를 마친 뒤 완료 요청에 그대로 보낸다.
	 * @param headers   업로드 요청에 그대로 넣어야 하는 헤더 (서명에 포함되어 값이 다르면 S3가 거절한다)
	 * @param expiresAt 이 시각이 지나면 주소를 쓸 수 없다.
	 */
	record PresignedUpload(String url, String key, Map<String, String> headers, Instant expiresAt) {
	}

	record UploadedObject(long size, byte[] head) {
	}
}
