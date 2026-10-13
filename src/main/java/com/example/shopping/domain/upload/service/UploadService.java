package com.example.shopping.domain.upload.service;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.shopping.domain.upload.dto.UploadResponse;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UploadService {

	private final FileStorage fileStorage;

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
