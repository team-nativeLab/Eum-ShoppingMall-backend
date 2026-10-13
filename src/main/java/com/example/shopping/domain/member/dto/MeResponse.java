package com.example.shopping.domain.member.dto;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberRole;

public record MeResponse(Long id, String email, String name, String phone, MemberRole role) {

	public static MeResponse from(Member member) {
		return new MeResponse(member.getId(), member.getEmail(), member.getName(), member.getPhone(),
				member.getRole());
	}
}
