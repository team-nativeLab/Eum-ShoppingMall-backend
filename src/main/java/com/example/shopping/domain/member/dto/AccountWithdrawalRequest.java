package com.example.shopping.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountWithdrawalRequest(@NotBlank @Size(max = 64) String password) {

	@Override
	public String toString() {
		return "AccountWithdrawalRequest[password=****]";
	}
}
