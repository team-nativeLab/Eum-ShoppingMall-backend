package com.example.shopping.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원 상태 변경
 *
 * @param status 바꿀 상태. ACTIVE(정상) 또는 SUSPENDED(정지)
 * @param reason 정지 사유(200자 이하). SUSPENDED면 필수이고, 회원 상세의 suspensionReason으로 보인다.
 */
public record AdminMemberStatusUpdateRequest(
		@NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED") String status,
		@Size(max = 200) String reason) {

	@Override
	public String toString() {
		return "AdminMemberStatusUpdateRequest[status=" + status + ", reason=" + (reason == null ? "null" : "****") + "]";
	}
}
