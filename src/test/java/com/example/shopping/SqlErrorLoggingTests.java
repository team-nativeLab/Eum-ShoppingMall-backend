package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.repository.MemberRepository;

/**
 * DB 중복 오류 메시지에는 겹친 값이 그대로 들어 있다(Duplicate entry 'user@example.com' ...).
 * Hibernate가 SQL 오류를 직접 남기는 로그(org.hibernate.orm.jdbc.error)에도 그 값이 남지 않는지 확인한다 (#139).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class SqlErrorLoggingTests {

	@Autowired
	private MemberRepository memberRepository;

	@Test
	void 유니크_제약에_걸려도_로그에_겹친_이메일이_남지_않는다(CapturedOutput output) {
		String email = "duplicate-leak@example.com";
		memberRepository.saveAndFlush(Member.create(email, "hash", "회원", null));

		assertThatThrownBy(() -> memberRepository.saveAndFlush(Member.create(email, "hash", "회원", null)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining(email);

		assertThat(output).doesNotContain(email);
	}
}
