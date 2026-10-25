package com.example.shopping.domain.upload.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.shopping.domain.upload.dto.request.PresignedUploadCompleteRequest;
import com.example.shopping.domain.upload.dto.request.PresignedUrlRequest;
import com.example.shopping.domain.upload.dto.response.PresignedUrlResponse;
import com.example.shopping.domain.upload.dto.response.UploadResponse;
import com.example.shopping.domain.upload.service.UploadService;
import com.example.shopping.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 경로가 /admin 밖이지만 모두 관리자 전용이다 (SecurityConfig의 /api/v1/uploads/**). */
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

	private final UploadService uploadService;

	/**
	 * ADM-006, 007 상품·배너 이미지 업로드 후 URL 반환.
	 * multipart/form-data의 file 필드에 이미지 하나를 담아 보낸다.
	 */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
		return ApiResponse.ok(uploadService.uploadImage(file));
	}

	/** S3에 직접 올릴 주소를 받는다 (upload.storage=s3일 때만). 올린 뒤에는 /complete를 호출해야 한다. */
	@PostMapping("/presigned-url")
	public ApiResponse<PresignedUrlResponse> getPresignedUrl(@Valid @RequestBody PresignedUrlRequest request) {
		return ApiResponse.ok(uploadService.getPresignedUrl(request));
	}

	/** 직접 올린 파일을 확인하고 이미지 주소를 받는다. 실제 이미지가 아니거나 5MB를 넘으면 파일을 지우고 400 */
	@PostMapping("/complete")
	public ApiResponse<UploadResponse> completePresignedUpload(
			@Valid @RequestBody PresignedUploadCompleteRequest request) {
		return ApiResponse.ok(uploadService.completePresignedUpload(request));
	}
}
