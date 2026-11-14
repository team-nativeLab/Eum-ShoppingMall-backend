package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.shopping.domain.product.repository.ProductSkuRepository;

/**
 * 재고·수량·금액이 음수가 되면 DB가 거절하는지 확인한다 (#143).
 * H2 테스트는 JPA가 스키마를 만들어 CHECK 제약이 없으므로, 운영과 같은 MySQL 8.4에 Flyway 스키마를 만들어 확인한다.
 * Docker가 없으면 건너뛴다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class AmountCheckConstraintMySqlTests {

	@Container
	private static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.4")
			.withEnv("MYSQL_DATABASE", "shopping")
			.withEnv("MYSQL_USER", "shopping")
			.withEnv("MYSQL_PASSWORD", "shopping")
			.withEnv("MYSQL_ROOT_PASSWORD", "root")
			// RDS 파라미터 그룹과 같게, 바이너리 로그가 켜진 MySQL에서도 마이그레이션이 트리거를 만들 수 있게 한다(V25, #131).
			.withCommand("--log-bin-trust-function-creators=1")
			.withExposedPorts(3306)
			.waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1)
					.withStartupTimeout(Duration.ofMinutes(3)));

	@DynamicPropertySource
	static void mysql(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/shopping");
		registry.add("spring.datasource.username", () -> "shopping");
		registry.add("spring.datasource.password", () -> "shopping");
		// 운영과 같은 스키마(CHECK 제약 포함)를 Flyway로 만든다.
		registry.add("spring.flyway.enabled", () -> "true");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ProductSkuRepository productSkuRepository;

	@Test
	void 재고_가격_수량이_음수가_되면_DB가_거절한다() {
		long skuId = insertSku();

		// 앱 코드(JPA)에서 어기면 DataIntegrityViolationException이 되어 409 CONCURRENT_UPDATE로 응답한다.
		assertThatThrownBy(() -> productSkuRepository.increaseStock(skuId, -11))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_product_sku_stock");
		assertRejected("ck_product_sku_price", () -> jdbcTemplate.update(
				"update product_sku set price = -1 where id = ?", skuId));
		assertRejected("ck_product_base_price", () -> jdbcTemplate.update(
				"update product set base_price = -1 where id = (select product_id from product_sku where id = ?)", skuId));
		assertRejected("ck_cart_item_quantity", () -> jdbcTemplate.update(
				"insert into cart_item (member_id, sku_id, quantity, selected) values (1, ?, 0, true)", skuId));

		// 경계값(재고 0, 가격 0, 수량 1)은 받는다.
		jdbcTemplate.update("update product_sku set stock = 0, price = 0 where id = ?", skuId);
		jdbcTemplate.update("insert into cart_item (member_id, sku_id, quantity, selected) values (1, ?, 1, true)", skuId);
		assertThat(jdbcTemplate.queryForObject("select stock from product_sku where id = ?", Integer.class, skuId))
				.isZero();
	}

	@Test
	void 재고_수량_금액_컬럼마다_CHECK_제약이_있다() {
		List<String> names = jdbcTemplate.queryForList("""
				select constraint_name from information_schema.table_constraints
				where table_schema = database() and constraint_type = 'CHECK'
				""", String.class);

		assertThat(names).containsExactlyInAnyOrder(
				"ck_product_base_price", "ck_product_list_price",
				"ck_product_sku_price", "ck_product_sku_stock",
				"ck_cart_item_quantity",
				"ck_orders_total_amount", "ck_orders_item_amount", "ck_orders_discount_amount", "ck_orders_shipping_fee",
				"ck_order_item_order_price", "ck_order_item_quantity",
				"ck_payment_approved_amount", "ck_payment_balance_amount",
				"ck_shipping_policy_shipping_fee", "ck_shipping_policy_free_shipping_threshold",
				// 리뷰 별점(1~5)과 상품의 리뷰 수·별점 합계(V28)
				"ck_product_review_rating", "ck_product_review_count");
	}

	private long insertSku() {
		jdbcTemplate.update("""
				insert into product (name, base_price, price_visible, status, created_at, updated_at)
				values ('제약 확인 상품', 1000, true, 'ON_SALE', now(), now())
				""");
		long productId = jdbcTemplate.queryForObject("select max(id) from product", Long.class);
		jdbcTemplate.update("""
				insert into product_sku (product_id, option_name, price, stock, version)
				values (?, '기본', 1000, 10, 0)
				""", productId);
		return jdbcTemplate.queryForObject("select id from product_sku where product_id = ?", Long.class, productId);
	}

	private static void assertRejected(String constraint, ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOf(DataAccessException.class).hasMessageContaining(constraint);
	}
}
