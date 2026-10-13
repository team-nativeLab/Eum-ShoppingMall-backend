package com.example.shopping.domain.upload.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.shopping.domain.upload.dto.UploadResponse;
import com.example.shopping.domain.upload.service.UploadService;
import com.example.shopping.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

	private final UploadService uploadService;

	/**
	 * ADM-006, 007 상품·배너 이미지 업로드 후 URL 반환. 경로가 /admin 밖이지만 관리자 전용이다.
	 * multipart/form-data의 file 필드에 이미지 하나를 담아 보낸다.
	 */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
		return ApiResponse.ok(uploadService.uploadImage(file));
	}
}
