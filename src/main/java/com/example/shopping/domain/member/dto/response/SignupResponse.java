package com.example.shopping.domain.member.dto.response;

import com.example.shopping.domain.member.entity.Member;

/**
 * 가입한 회원
 *
 * @param id    회원 ID
 * @param email 이메일
 * @param name  이름
 */
public record SignupResponse(Long id, String email, String name) {

	public static SignupResponse from(Member member) {
		return new SignupResponse(member.getId(), member.getEmail(), member.getName());
	}
}
