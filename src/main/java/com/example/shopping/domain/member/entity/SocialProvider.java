package com.example.shopping.domain.member.entity;

import java.util.Locale;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

/** 소셜 로그인 제공자(#225). 주소에는 소문자(kakao, google)로 쓴다. */
public enum SocialProvider {
	KAKAO, GOOGLE;

	/** 주소의 제공자 이름. 모르는 값이면 400(SOCIAL_PROVIDER_NOT_SUPPORTED) */
	public static SocialProvider fromPath(String value) {
		if (value != null) {
			for (SocialProvider provider : values()) {
				if (provider.name().equals(value.trim().toUpperCase(Locale.ROOT))) {
					return provider;
				}
			}
		}
		throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_NOT_SUPPORTED);
	}
}
