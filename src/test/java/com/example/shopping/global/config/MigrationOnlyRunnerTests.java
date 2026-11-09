package com.example.shopping.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

/** migrate 프로파일은 마이그레이션이 끝나면 종료 코드 0으로 끝난다 (#131). */
class MigrationOnlyRunnerTests {

	@Test
	void 컨텍스트를_닫고_종료_코드_0으로_끝낸다() {
		ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
		List<Integer> exitCodes = new ArrayList<>();

		new MigrationOnlyRunner(context, exitCodes::add).run(new DefaultApplicationArguments());

		verify(context).close();
		assertThat(exitCodes).containsExactly(0);
	}

	@Test
	void migrate_프로파일에서만_다른_시작_작업보다_먼저_돈다() {
		assertThat(MigrationOnlyRunner.class.getAnnotation(Profile.class).value()).containsExactly("migrate");
		assertThat(MigrationOnlyRunner.class.getAnnotation(Order.class).value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
	}
}
