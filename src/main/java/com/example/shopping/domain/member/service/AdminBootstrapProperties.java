package com.example.shopping.domain.member.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** 초기 관리자 계정 설정. 둘 다 값이 있을 때만 계정을 만든다. */
@ConfigurationProperties("admin.bootstrap")
public record AdminBootstrapProperties(String email, String password) {

	public boolean isConfigured() {
		return StringUtils.hasText(email) && StringUtils.hasText(password);
	}
}
