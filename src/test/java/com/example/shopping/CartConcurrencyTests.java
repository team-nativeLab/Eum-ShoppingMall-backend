package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.shopping.domain.cart.dto.request.CartItemAddRequest;
import com.example.shopping.domain.cart.service.CartService;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cart-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class CartConcurrencyTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CartService cartService;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Test
	void 같은_옵션을_동시에_담아도_수량이_모두_누적된다() throws Exception {
		String email = UUID.randomUUID() + "@example.com";
		String productName = "장바구니 동시성 " + UUID.randomUUID();
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values (?, 'not-used', '테스트 회원', 'USER', 'ACTIVE', now(), now())
				""", email);
		long memberId = jdbcTemplate.queryForObject("select id from member where email = ?", Long.class, email);
		jdbcTemplate.update("""
				insert into product (name, base_price, price_visible, status, created_at, updated_at)
				values (?, 1000, true, 'ON_SALE', now(), now())
				""", productName);
		long productId = jdbcTemplate.queryForObject("select id from product where name = ?", Long.class, productName);
		jdbcTemplate.update("""
				insert into product_sku (product_id, option_name, price, stock, version)
				values (?, '기본', 1000, 100, 0)
				""", productId);
		long skuId = jdbcTemplate.queryForObject("select id from product_sku where product_id = ?", Long.class,
				productId);

		ExecutorService pool = Executors.newFixedThreadPool(10);
		try {
			cartService.addItem(memberId, new CartItemAddRequest(skuId, 1));
			int requests = 10;
			CountDownLatch start = new CountDownLatch(1);
			List<Future<?>> results = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				results.add(pool.submit(() -> {
					start.await();
					cartService.addItem(memberId, new CartItemAddRequest(skuId, 1));
					return null;
				}));
			}
			start.countDown();
			for (Future<?> result : results) {
				result.get(30, TimeUnit.SECONDS);
			}

			Integer quantity = jdbcTemplate.queryForObject(
					"select quantity from cart_item where member_id = ? and sku_id = ?", Integer.class, memberId, skuId);
			assertThat(quantity).isEqualTo(11);
		} finally {
			pool.shutdownNow();
			jdbcTemplate.update("delete from cart_item where member_id = ?", memberId);
			jdbcTemplate.update("delete from product_sku where product_id = ?", productId);
			jdbcTemplate.update("delete from product where id = ?", productId);
			jdbcTemplate.update("delete from member where id = ?", memberId);
		}
	}

	@Test
	void 처음_담는_옵션을_같은_회원이_동시에_담아도_한_행에_수량이_모두_누적된다() throws Exception {
		long memberId = insertMember();
		long[] product = insertProductWithSku();
		ExecutorService pool = Executors.newFixedThreadPool(10);
		try {
			int requests = 10;
			CountDownLatch start = new CountDownLatch(1);
			List<Future<?>> results = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				results.add(pool.submit(() -> {
					start.await();
					cartService.addItem(memberId, new CartItemAddRequest(product[1], 1));
					return null;
				}));
			}
			start.countDown();
			for (Future<?> result : results) {
				result.get(30, TimeUnit.SECONDS);
			}

			assertThat(jdbcTemplate.queryForList("select quantity from cart_item where member_id = ?", Integer.class,
					memberId)).containsExactly(requests);
		} finally {
			pool.shutdownNow();
			cleanUp(new long[] { memberId }, product);
		}
	}

	@Test
	void 다른_회원은_같은_옵션을_담을_때_주문_등이_옵션_행을_잠그고_있어도_기다리지_않는다() throws Exception {
		long buyer = insertMember();
		long otherMember = insertMember();
		long[] product = insertProductWithSku();
		ExecutorService pool = Executors.newSingleThreadExecutor();
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try {
			// 주문 생성(재고 차감)이나 다른 회원의 담기처럼 옵션 행을 잡고 있는 트랜잭션
			Future<?> holder = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
				jdbcTemplate.queryForList("select id from product_sku where id = ? for update", product[1]);
				locked.countDown();
				try {
					release.await(30, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			long startedAt = System.nanoTime();
			cartService.addItem(otherMember, new CartItemAddRequest(product[1], 1));
			assertThat((System.nanoTime() - startedAt) / 1_000_000).isLessThan(1_000);

			release.countDown();
			holder.get(30, TimeUnit.SECONDS);
			assertThat(jdbcTemplate.queryForObject("select quantity from cart_item where member_id = ?",
					Integer.class, otherMember)).isEqualTo(1);
		} finally {
			release.countDown();
			pool.shutdownNow();
			cleanUp(new long[] { buyer, otherMember }, product);
		}
	}

	private long insertMember() {
		String email = UUID.randomUUID() + "@example.com";
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values (?, 'not-used', '테스트 회원', 'USER', 'ACTIVE', now(), now())
				""", email);
		return jdbcTemplate.queryForObject("select id from member where email = ?", Long.class, email);
	}

	/** @return {상품 ID, 옵션 ID} */
	private long[] insertProductWithSku() {
		String productName = "장바구니 동시성 " + UUID.randomUUID();
		jdbcTemplate.update("""
				insert into product (name, base_price, price_visible, status, created_at, updated_at)
				values (?, 1000, true, 'ON_SALE', now(), now())
				""", productName);
		long productId = jdbcTemplate.queryForObject("select id from product where name = ?", Long.class, productName);
		jdbcTemplate.update("""
				insert into product_sku (product_id, option_name, price, stock, version)
				values (?, '기본', 1000, 100, 0)
				""", productId);
		long skuId = jdbcTemplate.queryForObject("select id from product_sku where product_id = ?", Long.class,
				productId);
		return new long[] { productId, skuId };
	}

	private void cleanUp(long[] memberIds, long[] product) {
		for (long memberId : memberIds) {
			jdbcTemplate.update("delete from cart_item where member_id = ?", memberId);
			jdbcTemplate.update("delete from member where id = ?", memberId);
		}
		jdbcTemplate.update("delete from product_sku where product_id = ?", product[0]);
		jdbcTemplate.update("delete from product where id = ?", product[0]);
	}
}
