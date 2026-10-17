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

import com.example.shopping.domain.cart.dto.CartItemAddRequest;
import com.example.shopping.domain.cart.service.CartService;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cart-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class CartConcurrencyTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CartService cartService;

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
}
