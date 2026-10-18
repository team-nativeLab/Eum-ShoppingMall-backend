package com.example.shopping.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminMemberStatusUpdateRequest(
		@NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED") String status,
		@Size(max = 200) String reason) {

	@Override
	public String toString() {
		return "AdminMemberStatusUpdateRequest[status=" + status + ", reason=" + (reason == null ? "null" : "****") + "]";
	}
}
