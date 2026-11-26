package com.example.shopping.domain.member.dto.request;

import jakarta.validation.constraints.Size;

/** @param password 현재 비밀번호. 카카오·구글로만 가입해 비밀번호가 없는 계정은 보내지 않는다(#225). */
public record AccountWithdrawalRequest(@Size(max = 64) String password) {

	@Override
	public String toString() {
		return "AccountWithdrawalRequest[password=****]";
	}
}
