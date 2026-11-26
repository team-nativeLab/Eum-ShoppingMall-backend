package com.example.shopping.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 회원 권한 변경
 *
 * @param role 바꿀 권한. USER(회원) 또는 ADMIN(관리자)
 */
public record AdminMemberRoleUpdateRequest(
		@NotBlank @Pattern(regexp = "USER|ADMIN", message = "USER 또는 ADMIN만 지정할 수 있습니다.") String role) {
}
