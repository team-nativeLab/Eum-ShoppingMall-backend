package com.example.shopping.domain.member.dto.response;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberRole;

/**
 * 내 정보
 *
 * @param id          회원 ID
 * @param email       이메일(로그인 식별자)
 * @param name        이름
 * @param phone       연락처. 없으면 null
 * @param role        권한. USER(회원), ADMIN(관리자)
 * @param hasPassword 비밀번호가 있는 계정인지(#225). 카카오·구글로만 가입했으면 false라, 비밀번호 변경 대신 비밀번호 찾기로 정하고 탈퇴할 때 비밀번호를 보내지 않는다.
 */
public record MeResponse(Long id, String email, String name, String phone, MemberRole role, boolean hasPassword) {

	public static MeResponse from(Member member) {
		return new MeResponse(member.getId(), member.getEmail(), member.getName(), member.getPhone(),
				member.getRole(), member.hasPassword());
	}
}
