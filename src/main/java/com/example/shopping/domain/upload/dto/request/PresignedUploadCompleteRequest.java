package com.example.shopping.domain.upload.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param fileKey 업로드 주소를 받을 때 응답의 fileKey */
public record PresignedUploadCompleteRequest(
		@NotBlank(message = "fileKey를 입력해 주세요.")
		@Size(max = 300, message = "fileKey가 올바르지 않습니다.")
		String fileKey) {
}
