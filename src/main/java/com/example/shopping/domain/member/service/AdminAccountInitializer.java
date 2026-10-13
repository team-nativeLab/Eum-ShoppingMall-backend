package com.example.shopping.domain.member.service;

import java.util.Locale;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리자 계정은 회원가입으로 만들 수 없으므로, 설정(admin.bootstrap)에 값이 있으면 기동할 때 한 번 만들어 둔다.
 * 같은 이메일의 계정이 이미 있으면 건드리지 않는다 (일반 회원을 관리자로 올리거나 비밀번호를 덮어쓰지 않는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class AdminAccountInitializer implements ApplicationRunner {

	private static final int MIN_PASSWORD_LENGTH = 12;

	private final AdminBootstrapProperties properties;
	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.isConfigured()) {
			return;
		}
		if (properties.password().length() < MIN_PASSWORD_LENGTH) {
			// 짧은 비밀번호로 관리자 계정이 만들어지는 것을 막기 위해 기동을 중단한다.
			throw new IllegalStateException("관리자 초기 비밀번호(ADMIN_PASSWORD)는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
		}
		String email = properties.email().trim().toLowerCase(Locale.ROOT);
		if (memberRepository.existsByEmail(email)) {
			return;
		}
		memberRepository.save(Member.createAdmin(email, passwordEncoder.encode(properties.password()), "관리자"));
		log.info("초기 관리자 계정을 만들었습니다: {}", email);
	}
}
