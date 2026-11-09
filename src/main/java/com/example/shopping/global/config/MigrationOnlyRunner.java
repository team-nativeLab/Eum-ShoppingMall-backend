package com.example.shopping.global.config;

import java.util.function.IntConsumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * migrate 프로파일로 띄우면 마이그레이션만 하고 끝낸다(#131).
 * 컨텍스트가 뜨기까지 Flyway가 마이그레이션을 적용하고 Hibernate가 스키마를 검증하므로, 여기까지 오면 둘 다 성공한 것이다.
 * 다른 시작 작업(초기 관리자 계정, 기본 카테고리)보다 먼저 끝내, 마이그레이션 계정으로는 데이터를 바꾸지 않는다.
 */
@Slf4j
@Component
@Profile("migrate")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MigrationOnlyRunner implements ApplicationRunner {

	private final ConfigurableApplicationContext context;
	private final IntConsumer exit;

	@Autowired
	public MigrationOnlyRunner(ConfigurableApplicationContext context) {
		this(context, System::exit);
	}

	MigrationOnlyRunner(ConfigurableApplicationContext context, IntConsumer exit) {
		this.context = context;
		this.exit = exit;
	}

	@Override
	public void run(ApplicationArguments args) {
		log.info("DB 마이그레이션과 스키마 검증을 마쳤습니다. 마이그레이션 작업을 끝냅니다.");
		exit.accept(SpringApplication.exit(context, () -> 0));
	}
}
