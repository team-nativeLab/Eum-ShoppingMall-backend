package com.example.shopping.domain.member.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberRole;
import com.example.shopping.domain.member.entity.MemberStatus;

/** 목록에는 연락처를 싣지 않는다. 연락처는 상세 조회에서만 내려준다. */
public record AdminMemberSummaryResponse(Long id, String email, String name, MemberRole role, MemberStatus status,
		LocalDateTime createdAt) {

	public static AdminMemberSummaryResponse from(Member member) {
		return new AdminMemberSummaryResponse(member.getId(), member.getEmail(), member.getName(), member.getRole(),
				member.getStatus(), member.getCreatedAt());
	}
}
