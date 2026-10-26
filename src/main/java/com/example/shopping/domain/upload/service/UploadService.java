package com.example.shopping.domain.upload.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.example.shopping.domain.upload.dto.request.PresignedUploadCompleteRequest;
import com.example.shopping.domain.upload.dto.request.PresignedUrlRequest;
import com.example.shopping.domain.upload.dto.response.PresignedUrlResponse;
import com.example.shopping.domain.upload.dto.response.UploadResponse;
import com.example.shopping.domain.upload.service.PresignedUploadStorage.PresignedUpload;
import com.example.shopping.domain.upload.service.PresignedUploadStorage.UploadedObject;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UploadService {

	/** 올릴 수 있는 최대 크기(5MB). 서버를 거치는 업로드의 multipart 설정과 같게 맞췄다. */
	public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

	// 이미지 종류를 판별하는 데 필요한 파일 앞부분의 길이 (WEBP가 가장 길다)
	private static final int SIGNATURE_BYTES = 12;

	private static final Map<String, String> EXTENSIONS = Map.of(
			"image/jpeg", "jpg",
			"image/png", "png",
			"image/gif", "gif",
			"image/webp", "webp");

	private final FileStorage fileStorage;

	// upload.storage=s3일 때만 있다.
	private final ObjectProvider<PresignedUploadStorage> presignedUploadStorage;

	/** JPG, PNG, GIF, WEBP 이미지만 받는다. */
	public UploadResponse uploadImage(MultipartFile file) {
		byte[] content;
		try {
			content = file.getBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		String extension = detectImageExtension(content);
		if (extension == null) {
			throw new BusinessException(ErrorCode.INVALID_FILE);
		}
		return new UploadResponse(fileStorage.store(content, extension));
	}

	/**
	 * 서버를 거치지 않고 S3에 직접 올릴 주소를 만든다. 형식과 크기는 서명에 들어가므로 다르게 올리면 S3가 거절한다.
	 * 다 올린 뒤 completePresignedUpload로 확인을 받아야 이미지 주소를 받을 수 있다.
	 */
	public PresignedUrlResponse getPresignedUrl(PresignedUrlRequest request) {
		PresignedUpload upload = presignedStorage().presign(EXTENSIONS.get(request.contentType()),
				request.contentType(), request.contentLength());
		return PresignedUrlResponse.from(upload);
	}

	/**
	 * 직접 올린 파일을 확인하고 이미지 주소를 돌려준다. 서버가 파일 내용을 보지 못한 채 올라왔으므로
	 * 서버를 거치는 업로드와 같은 기준(실제 이미지인지, 5MB 이하인지)으로 다시 검사하고, 통과하지 못하면 지운다.
	 */
	public UploadResponse completePresignedUpload(PresignedUploadCompleteRequest request) {
		PresignedUploadStorage storage = presignedStorage();
		String key = request.fileKey();
		UploadedObject uploaded = storage.inspect(key, SIGNATURE_BYTES);

		String extension = detectImageExtension(uploaded.head());
		// 경로의 확장자는 업로드 주소를 받을 때 고른 형식이다. 실제 내용이 그 형식과 달라도 거절한다.
		if (uploaded.size() == 0 || uploaded.size() > MAX_FILE_BYTES || extension == null
				|| !key.endsWith("." + extension)) {
			storage.delete(key);
			throw new BusinessException(ErrorCode.INVALID_FILE);
		}
		return new UploadResponse(storage.publicUrl(key));
	}

	/**
	 * 상품·배너 이미지 주소는 업로드 API가 돌려준 주소만 받는다. 빈 값(이미지 없음)은 통과시킨다.
	 * 외부 주소를 막아 두면 이미지가 사라지거나 바뀌는 일이 없고, 참조 없는 이미지 정리가 우리 파일만 다루면 된다.
	 */
	public void requireUploadedImageUrl(String url) {
		if (StringUtils.hasText(url) && !fileStorage.isStoredUrl(url)) {
			throw new BusinessException(ErrorCode.INVALID_IMAGE_URL);
		}
	}

	private PresignedUploadStorage presignedStorage() {
		PresignedUploadStorage storage = presignedUploadStorage.getIfAvailable();
		if (storage == null) {
			throw new BusinessException(ErrorCode.PRESIGNED_UPLOAD_NOT_SUPPORTED);
		}
		return storage;
	}

	/**
	 * 파일 내용의 앞부분(시그니처)으로 이미지 종류를 판별한다.
	 * 클라이언트가 보낸 파일명과 Content-Type은 얼마든지 꾸밀 수 있어 믿지 않는다.
	 */
	private String detectImageExtension(byte[] content) {
		if (startsWith(content, 0xFF, 0xD8, 0xFF)) {
			return "jpg";
		}
		if (startsWith(content, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
			return "png";
		}
		if (startsWith(content, 'G', 'I', 'F', '8')) {
			return "gif";
		}
		if (content.length >= 12 && startsWith(content, 'R', 'I', 'F', 'F')
				&& content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
			return "webp";
		}
		return null;
	}

	private boolean startsWith(byte[] content, int... signature) {
		if (content.length < signature.length) {
			return false;
		}
		for (int i = 0; i < signature.length; i++) {
			if ((content[i] & 0xFF) != signature[i]) {
				return false;
			}
		}
		return true;
	}
}
