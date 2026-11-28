package com.example.shopping.domain.member.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberRole;

/**
 * 내 정보
 *
 * @param id              회원 ID
 * @param email           이메일(로그인 식별자)
 * @param name            이름
 * @param phone           연락처. 없으면 null
 * @param role            권한. USER(회원), ADMIN(관리자)
 * @param hasPassword     비밀번호가 있는 계정인지(#225). 카카오·구글로만 가입했으면 false라, 비밀번호 변경 대신 비밀번호 찾기로 정하고
 *                        탈퇴할 때 비밀번호를 보내지 않는다.
 * @param joinedAt        가입 시각(#229)
 * @param marketingAgreed 지금 마케팅 정보 수신에 동의한 상태인지(#229). 바꾸려면 PUT /api/v1/me/marketing-consent
 */
public record MeResponse(Long id, String email, String name, String phone, MemberRole role, boolean hasPassword,
		LocalDateTime joinedAt, boolean marketingAgreed) {

	public static MeResponse of(Member member, boolean marketingAgreed) {
		return new MeResponse(member.getId(), member.getEmail(), member.getName(), member.getPhone(),
				member.getRole(), member.hasPassword(), member.getCreatedAt(), marketingAgreed);
	}
}
