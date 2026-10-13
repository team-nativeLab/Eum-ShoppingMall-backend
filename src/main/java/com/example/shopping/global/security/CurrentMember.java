package com.example.shopping.global.security;

import org.springframework.security.oauth2.jwt.Jwt;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

/** 컨트롤러가 @AuthenticationPrincipal Jwt로 받은 토큰에서 로그인 회원 ID를 꺼낸다. */
public final class CurrentMember {

	private CurrentMember() {
	}

	public static Long id(Jwt jwt) {
		if (jwt == null) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		return Long.valueOf(jwt.getSubject());
	}

	/** 비회원도 호출할 수 있는 API용. 로그인하지 않았으면 null */
	public static Long idOrNull(Jwt jwt) {
		return jwt == null ? null : Long.valueOf(jwt.getSubject());
	}
}
