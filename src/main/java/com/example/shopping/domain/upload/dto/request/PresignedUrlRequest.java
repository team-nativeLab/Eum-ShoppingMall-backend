package com.example.shopping.domain.upload.dto.request;

import com.example.shopping.domain.upload.service.UploadService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 저장 경로는 서버가 정하므로 파일 이름은 받지 않는다.
 *
 * @param contentType   올릴 이미지의 형식. 업로드 요청의 Content-Type 헤더와 같아야 한다.
 * @param contentLength 올릴 파일의 크기(바이트). 업로드 요청의 Content-Length와 같아야 한다.
 */
public record PresignedUrlRequest(
		@NotBlank(message = "파일 형식을 입력해 주세요.")
		@Pattern(regexp = "^image/(jpeg|png|gif|webp)$", message = "JPG, PNG, GIF, WEBP 이미지만 올릴 수 있습니다.")
		String contentType,

		@NotNull(message = "파일 크기를 입력해 주세요.")
		@Positive(message = "빈 파일은 올릴 수 없습니다.")
		@Max(value = UploadService.MAX_FILE_BYTES, message = "5MB 이하의 파일만 올릴 수 있습니다.")
		Long contentLength) {
}
