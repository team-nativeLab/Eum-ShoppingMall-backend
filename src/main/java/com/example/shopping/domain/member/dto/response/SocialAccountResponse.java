package com.example.shopping.domain.member.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.member.entity.MemberSocialAccount;
import com.example.shopping.domain.member.entity.SocialProvider;

/**
 * 내 계정에 연결한 카카오·구글 계정(#225)
 *
 * @param provider 연결한 계정의 카카오·구글 중 어느 쪽인지(KAKAO, GOOGLE)
 * @param email    연결할 때 카카오·구글이 알려 준 이메일
 * @param linkedAt 연결한 시각
 */
public record SocialAccountResponse(SocialProvider provider, String email, LocalDateTime linkedAt) {

	public static SocialAccountResponse from(MemberSocialAccount account) {
		return new SocialAccountResponse(account.getProvider(), account.getEmail(), account.getCreatedAt());
	}
}
