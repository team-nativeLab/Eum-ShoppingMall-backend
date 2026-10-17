package com.example.shopping.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminMemberRoleUpdateRequest(
		@NotBlank @Pattern(regexp = "USER|ADMIN", message = "USER 또는 ADMIN만 지정할 수 있습니다.") String role) {
}
