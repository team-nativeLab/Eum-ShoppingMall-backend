package com.example.shopping.domain.member.social;

import com.example.shopping.domain.member.entity.SocialProvider;

/**
 * 카카오·구글이 확인해 준 사용자(#225)
 *
 * @param providerUserId 카카오 회원번호, 구글 sub
 * @param email          제공자가 알려 준 이메일. 사용자가 제공에 동의하지 않았으면 null
 * @param emailVerified  제공자가 인증한 이메일인지. 같은 이메일의 기존 회원에 자동으로 연결할지 정한다.
 * @param name           이름(없으면 닉네임). 가입 화면의 기본값
 */
public record SocialProfile(SocialProvider provider, String providerUserId, String email, boolean emailVerified,
		String name) {
}
