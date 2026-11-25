package com.example.shopping.domain.home.service;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.shopping.domain.upload.service.UploadService;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 홈에 그리는 이미지(홈 편성, 팝업)가 쓸 수 있는 주소. 업로드 API가 돌려준 주소나 웹의 정적 이미지만 받는다. */
@Component
@RequiredArgsConstructor
public class HomeImagePolicy {

	/**
	 * 웹(Next.js)이 함께 배포하는 정적 이미지(예: /assets/web/hero-v3.webp). 사이트 안 경로라 다른 사이트의 이미지를 끌어오지 않는다.
	 * 경로를 거슬러 올라가지 못하게 영문·숫자·-·_ 이름과 이미지 확장자만 받는다.
	 */
	private static final Pattern STATIC_ASSET_IMAGE = Pattern
			.compile("^/assets/[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*\\.(webp|png|jpe?g|gif|svg|avif)$");

	private final UploadService uploadService;

	/** 둘 다 아니면 400(INVALID_HOME_IMAGE_URL) */
	public void require(String url) {
		if (!STATIC_ASSET_IMAGE.matcher(url).matches() && !uploadService.isUploadedImageUrl(url)) {
			throw new BusinessException(ErrorCode.INVALID_HOME_IMAGE_URL);
		}
	}
}
