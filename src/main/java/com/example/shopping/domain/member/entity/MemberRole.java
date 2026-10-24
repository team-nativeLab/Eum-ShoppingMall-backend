package com.example.shopping.domain.member.entity;

public enum MemberRole {
	USER, ADMIN, SUPER_ADMIN;

	public boolean canAccessAdminApi() {
		return this == ADMIN || this == SUPER_ADMIN;
	}
}
