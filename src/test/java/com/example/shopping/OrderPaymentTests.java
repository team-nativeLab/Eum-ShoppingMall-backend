package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.service.OrderMaintenanceScheduler;
import com.example.shopping.domain.payment.gateway.PaymentGateway;
import com.example.shopping.domain.payment.gateway.PgPayment;
import com.example.shopping.domain.payment.gateway.PgPayment.PgStatus;
import com.example.shopping.domain.payment.gateway.PgRejectedException;
import com.example.shopping.domain.payment.gateway.PgUnavailableException;
import com.example.shopping.domain.payment.gateway.PgVirtualAccount;
import com.example.shopping.domain.product.entity.ProductSku;
import com.example.shopping.domain.product.repository.ProductSkuRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/** 주문·결제·취소 흐름. 실제 PG 대신 가짜 PaymentGateway를 끼워 PG의 응답 상황별 처리를 검증한다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:orders;MODE=MySQL")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderPaymentTests {

	private static final String PAYMENT_KEY = "pay-key-0001";

	// 주문은 실제로 있는 회원만 만들 수 있으므로(FK 제약) 테스트마다 회원 두 명을 만들어 둔다.
	private long buyer;
	private long other;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private OrderMaintenanceScheduler scheduler;

	@Autowired
	private ProductSkuRepository productSkuRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@MockitoBean
	private PaymentGateway paymentGateway;

	@BeforeEach
	void createMembers() {
		createSchedulerLocks();
		buyer = insertMember();
		other = insertMember();
	}

	private void createSchedulerLocks() {
		jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS scheduler_lock (
					lock_name VARCHAR(100) PRIMARY KEY,
					lock_until TIMESTAMP(6) NOT NULL,
					lock_owner VARCHAR(36),
					last_order_id BIGINT NOT NULL DEFAULT 0
				)
				""");
		jdbcTemplate.update("DELETE FROM scheduler_lock");
		jdbcTemplate.update("""
				INSERT INTO scheduler_lock (lock_name, lock_until, lock_owner, last_order_id)
				VALUES
					('reconcileStuckPayments', DATEADD(SECOND, -1, CURRENT_TIMESTAMP), NULL, 0),
					('reconcileStuckCancellations', DATEADD(SECOND, -1, CURRENT_TIMESTAMP), NULL, 0),
					('purgeExpiredRefreshTokens', DATEADD(SECOND, -1, CURRENT_TIMESTAMP), NULL, 0),
					('reconcileWaitingDeposits', DATEADD(SECOND, -1, CURRENT_TIMESTAMP), NULL, 0)
				""");
	}

	@Test
	void 배송비와_할인은_상품합계_기준으로_계산되어_주문상세에_저장된다() throws Exception {
		long[] prices = {99999, 100000, 100001};
		long[] discounts = {999, 1000, 1000};
		long[] shippingFees = {5000, 0, 0};
		long[] totals = {104000, 99000, 99001};
		for (int i = 0; i < prices.length; i++) {
			jdbcTemplate.update("delete from cart_item where member_id = ?", buyer);
			String orderNo = createOrder(buyer,
					insertProductWithSku("가격 정책 테스트 " + i, prices[i], true, 5), 1);
			mockMvc.perform(get("/api/v1/orders/{orderNo}", orderNo).with(member(buyer)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.itemAmount").value(prices[i]))
					.andExpect(jsonPath("$.data.discountAmount").value(discounts[i]))
					.andExpect(jsonPath("$.data.shippingFee").value(shippingFees[i]))
					.andExpect(jsonPath("$.data.totalAmount").value(totals[i]));
		}
	}

	// ───────── 주문 생성 ─────────

	@Test
	void 선택한_품목_중_주문_가능한_것만_서버가_계산한_금액으로_주문하고_재고를_잡아_둔다() throws Exception {
		long helmetSku = insertProductWithSku("안전모", 30000, true, 5);
		long hiddenSku = insertProductWithSku("견적 전용 장비", 987654, false, 5);
		long vestSku = insertProductWithSku("안전 조끼", 20000, true, 5);
		addToCart(buyer, helmetSku, 2);
		addToCart(buyer, hiddenSku, 1);
		addToCart(buyer, vestSku, 1);
		// 조끼는 선택 해제
		mockMvc.perform(patch("/api/v1/cart/items/{skuId}", vestSku).with(member(buyer))
				.contentType(MediaType.APPLICATION_JSON).content("{\"selected\":false}"));

		// 배송지를 입력하기 전에는 주문할 수 없다.
		mockMvc.perform(post("/api/v1/orders").with(member(buyer)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DELIVERY_REQUIRED"));
		saveDelivery(buyer);

		String body = mockMvc.perform(post("/api/v1/orders").with(member(buyer)).header("Idempotency-Key", "key-1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.amount").value(64400))
				.andExpect(jsonPath("$.data.orderName").value("안전모"))
				.andReturn().getResponse().getContentAsString();
		String orderNo = JsonPath.read(body, "$.data.orderNo");

		assertThat(stockOf(helmetSku)).isEqualTo(3);
		assertThat(stockOf(hiddenSku)).isEqualTo(5);
		assertThat(stockOf(vestSku)).isEqualTo(5);

		// 주문 버튼을 두 번 눌러도(같은 키) 주문은 하나이고 재고도 한 번만 줄어든다.
		mockMvc.perform(post("/api/v1/orders").with(member(buyer)).header("Idempotency-Key", "key-1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.orderNo").value(orderNo));
		assertThat(stockOf(helmetSku)).isEqualTo(3);

		mockMvc.perform(get("/api/v1/orders/{orderNo}", orderNo).with(member(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].orderPrice").value(30000))
				.andExpect(jsonPath("$.data.shippingAddress.recipient").value("홍길동"))
				.andExpect(jsonPath("$.data.shippingAddress.recipientPhone").value("01012345678"))
				.andExpect(content().string(not(containsString("987654"))));
		mockMvc.perform(get("/api/v1/orders").with(member(buyer)))
				.andExpect(jsonPath("$.data.totalElements").value(1));
	}

	@Test
	void 재고가_모자라거나_주문_가능한_품목이_없으면_주문을_거절한다() throws Exception {
		long scarceSku = insertProductWithSku("안전화", 50000, true, 1);
		long hiddenSku = insertProductWithSku("견적 전용 장비", 500000, false, 5);
		saveDelivery(buyer);

		addToCart(buyer, hiddenSku, 1);
		mockMvc.perform(post("/api/v1/orders").with(member(buyer)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NO_ORDERABLE_ITEMS"));

		addToCart(buyer, scarceSku, 2);
		mockMvc.perform(post("/api/v1/orders").with(member(buyer)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
		assertThat(stockOf(scarceSku)).isEqualTo(1);

		mockMvc.perform(post("/api/v1/checkout/delivery").with(member(buyer)).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"recipient":"홍길동","recipientPhone":"전화","zipCode":"123","address":""}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 결제하지_않은_주문을_두고_다시_주문하면_이전_주문은_만료되고_잡아_둔_재고가_풀린다() throws Exception {
		long sku = insertProductWithSku("안전모", 30000, true, 5);
		String othersOrder = createOrder(other, sku, 1);
		String first = createOrder(buyer, sku, 2);
		assertThat(stockOf(sku)).isEqualTo(2);

		// 장바구니는 그대로 둔 채 주문만 다시 만든다. (주문을 반복해 재고를 묶어 두려는 시도)
		String body = mockMvc.perform(post("/api/v1/orders").with(member(buyer)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String second = JsonPath.read(body, "$.data.orderNo");

		assertThat(statusOf(first)).isEqualTo("EXPIRED");
		assertThat(statusOf(second)).isEqualTo("PENDING");
		assertThat(statusOf(othersOrder)).isEqualTo("PENDING");
		assertThat(stockOf(sku)).isEqualTo(2);
	}

	@Test
	void 금액이_0원인_주문과_정지된_회원의_주문은_만들_수_없다() throws Exception {
		long freeSku = insertProductWithSku("무료 샘플", 0, true, 5);
		addToCart(buyer, freeSku, 1);
		saveDelivery(buyer);

		mockMvc.perform(post("/api/v1/orders").with(member(buyer)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORDER_AMOUNT_INVALID"));
		assertThat(stockOf(freeSku)).isEqualTo(5);

		jdbcTemplate.update("update member set status = 'SUSPENDED' where id = ?", buyer);
		entityManager.clear();
		mockMvc.perform(post("/api/v1/orders").with(member(buyer)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
	}

	@Test
	void 수량_제한_없이_재고만큼_주문할_수_있다() throws Exception {
		long sku = insertProductWithSku("코팅 장갑", 1500, true, 500);

		String orderNo = createOrder(buyer, sku, 300);

		assertThat(jdbcTemplate.queryForObject("select total_amount from orders where order_no = ?", Long.class,
				orderNo)).isEqualTo(445_500);
		assertThat(stockOf(sku)).isEqualTo(200);
	}

	@Test
	void 결제_금액이_상한을_넘거나_계산이_long_범위를_넘으면_주문을_만들지_않는다() throws Exception {
		long expensive = insertProductWithSku("고가 장비", Order.MAX_TOTAL_AMOUNT, true, 5);
		addToCart(buyer, expensive, 2);
		saveDelivery(buyer);

		mockMvc.perform(post("/api/v1/orders").with(member(buyer)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ORDER_AMOUNT_TOO_LARGE"));
		assertThat(stockOf(expensive)).isEqualTo(5);

		// 1조 원 × 10억 개는 long 범위를 넘는다. 장바구니 금액은 음수로 뒤집히지 않고, 주문은 거절된다.
		long huge = insertProductWithSku("대량 재고 장비", Order.MAX_TOTAL_AMOUNT, true, ProductSku.MAX_STOCK);
		addToCart(other, huge, ProductSku.MAX_STOCK);
		saveDelivery(other);
		mockMvc.perform(get("/api/v1/cart").with(member(other)))
				.andExpect(jsonPath("$.data.items[0].amount").value(Long.MAX_VALUE))
				.andExpect(jsonPath("$.data.totalAmount").value(Long.MAX_VALUE));
		mockMvc.perform(post("/api/v1/orders").with(member(other)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ORDER_AMOUNT_TOO_LARGE"));
		assertThat(stockOf(huge)).isEqualTo(ProductSku.MAX_STOCK);
	}

	@Test
	void 남의_주문은_조회도_결제도_취소도_할_수_없고_없는_주문처럼_응답한다() throws Exception {
		String orderNo = createOrder(buyer, insertProductWithSku("안전모", 30000, true, 5), 1);

		mockMvc.perform(get("/api/v1/orders/{orderNo}", orderNo).with(member(other)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
		confirm(other, orderNo, 34700)
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/orders/{orderNo}/cancel", orderNo).with(member(other)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/orders").with(member(other)))
				.andExpect(jsonPath("$.data.totalElements").value(0));
		verify(paymentGateway, never()).confirm(anyString(), anyString(), anyLong());
	}

	// ───────── 결제 승인 ─────────

	@Test
	void 결제를_승인하면_결제완료가_되고_장바구니에서_빠지며_같은_요청이_다시_와도_한_번만_승인한다() throws Exception {
		long helmetSku = insertProductWithSku("안전모", 30000, true, 5);
		long hiddenSku = insertProductWithSku("견적 전용 장비", 500000, false, 5);
		addToCart(buyer, hiddenSku, 1);
		String orderNo = createOrder(buyer, helmetSku, 2);
		when(paymentGateway.confirm(PAYMENT_KEY, orderNo, 64400)).thenReturn(pg(orderNo, PgStatus.DONE, 64400));

		confirm(buyer, orderNo, 64400)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PAID"))
				.andExpect(jsonPath("$.data.payment.method").value("CARD"))
				.andExpect(jsonPath("$.data.payment.approvedAmount").value(64400))
				.andExpect(jsonPath("$.data.paidAt").isNotEmpty());

		// 결제한 수량만큼 누적 판매 수량(인기순 기준)이 오른다.
		assertThat(salesCountOf(helmetSku)).isEqualTo(2);
		assertThat(salesCountOf(hiddenSku)).isZero();

		// 결제한 품목만 장바구니에서 빠지고 금액 미표시 상품은 남는다.
		mockMvc.perform(get("/api/v1/cart").with(member(buyer)))
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].status").value("PRICE_HIDDEN"));

		// 결제 완료 화면 새로고침
		confirm(buyer, orderNo, 64400)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PAID"));
		verify(paymentGateway, times(1)).confirm(anyString(), anyString(), anyLong());
	}

	@Test
	void 요청_금액이_주문_금액과_다르면_PG를_호출하지_않고_거절한다() throws Exception {
		String orderNo = createOrder(buyer, insertProductWithSku("안전모", 30000, true, 5), 2);

		confirm(buyer, orderNo, 100)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));

		verify(paymentGateway, never()).confirm(anyString(), anyString(), anyLong());
		assertThat(statusOf(orderNo)).isEqualTo("PENDING");
	}

	@Test
	void PG가_승인한_금액이_주문_금액과_다르면_바로_환불하고_실패_처리한다() throws Exception {
		String orderNo = createOrder(buyer, insertProductWithSku("안전모", 30000, true, 5), 2);
		when(paymentGateway.confirm(PAYMENT_KEY, orderNo, 64400)).thenReturn(pg(orderNo, PgStatus.DONE, 100));

		confirm(buyer, orderNo, 64400)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));

		verify(paymentGateway).cancel(eq(PAYMENT_KEY), anyString(), anyString());
		assertThat(statusOf(orderNo)).isEqualTo("PENDING");
		assertThat(jdbcTemplate.queryForObject("select count(*) from payment", Long.class)).isZero();
	}

	@Test
	void PG가_다른_주문의_결제를_승인했다고_응답하면_환불하고_실패_처리한다() throws Exception {
		String orderNo = createOrder(buyer, insertProductWithSku("안전모", 30000, true, 5), 1);
		when(paymentGateway.confirm(PAYMENT_KEY, orderNo, 34700))
				.thenReturn(pg("SOME-OTHER-ORDER", PgStatus.DONE, 34700));

		confirm(buyer, orderNo, 34700)
				.andExpect(status().is(422))
				.andExpect(jsonPath("$.code").value("PAYMENT_FAILED"));

		verify(paymentGateway).cancel(eq(PAYMENT_KEY), anyString(), anyString());
		assertThat(statusOf(orderNo)).isEqualTo("PENDING");
		assertThat(jdbcTemplate.queryForObject("select count(*) from payment", Long.class)).isZero();
	}

	@Test
	void PG가_거절하면_실패로_응답하고_다시_결제할_수_있다() throws Exception {
		String orderNo = createOrder(buyer, insertProductWithSku("안전모", 30000, true, 5), 1);
		when(paymentGateway.confirm(PAYMENT_KEY, orderNo, 34700))
				.thenThrow(new PgRejectedException("REJECT_CARD_COMPANY", null))
				.thenReturn(pg(orderNo, PgStatus.DONE, 34700));
		when(paymentGateway.findByOrderNo(orderNo)).thenReturn(Optional.empty());

		confirm(buyer, orderNo, 34700)
				.andExpect(status().is(422))
				.andExpect(jsonPath("$.code").value("PAYMENT_FAILED"));
		assertThat(statusOf(orderNo)).isEqualTo("PENDING");

		confirm(buyer, orderNo, 34700)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PAID"));
	}

	@Test
	void PG_응답을_못_받았어도_실제로_승인됐으면_결제완료로_처리한다() throws Exception {
		String orderNo = createOrder(buyer, insertProductWithSku("안전모", 30000, true, 5), 1);
		when(paymentGateway.confirm(PAYMENT_KEY, orderNo, 34700)).thenThrow(new PgUnavailableException("timeout", null));
		when(paymentGateway.findByOrderNo(orderNo)).thenReturn(Optional.of(pg(orderNo, PgStatus.DONE, 34700)));

		confirm(buyer, orderNo, 34700)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PAID"));
	}

	@Test
	void 승인을_기다리는_동안_보정_작업이_결제_전으로_되돌렸으면_늦게_온_승인을_다시_반영한다() throws Exception {
		String orderNo = createOrder(buyer, insertProductWithSku("안전모", 30000, true, 5), 1);
		when(paymentGateway.confirm(PAYMENT_KEY, orderNo, 34700)).thenAnswer(invocation -> {
			jdbcTemplate.update("update orders set status = 'PENDING' where order_no = ?", orderNo);
			return pg(orderNo, PgStatus.DONE, 34700);
		});

		confirm(buyer, orderNo, 34700)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PAID"))
				.andExpect(jsonPath("$.data.payment.approvedAmount").value(34700));
		verify(paymentGateway, never()).cancel(anyString(), anyString(), anyString());
	}

	@Test
	void 승인을_기다리는_동안_주문이_만료됐으면_돈만_빠져나가지_않게_승인을_취소한다() throws Exception {
		String orderNo = createOrder(buyer, insertProductWithSku("안전모", 30000, true, 5), 1);
		when(paymentGateway.confirm(PAYMENT_KEY, orderNo, 34700)).thenAnswer(invocation -> {
			jdbcTemplate.update("update orders set status = 'EXPIRED' where order_no = ?", orderNo);
			return pg(orderNo, PgStatus.DONE, 34700);
		});
		when(paymentGateway.cancel(PAYMENT_KEY, "주문에 결제를 반영하지 못함", "unapplied-" + orderNo))
				.thenReturn(pg(orderNo, PgStatus.CANCELED, 34700));

		confirm(buyer, orderNo, 34700)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PAYMENT_FAILED"));
		verify(paymentGateway).cancel(PAYMENT_KEY, "주문에 결제를 반영하지 못함", "unapplied-" + orderNo);
		assertThat(statusOf(orderNo)).isEqualTo("EXPIRED");
	}

	@Test
	void 반영하지_못한_승인을_취소하지도_못하면_확인_중으로_알린다() throws Exception {
		String orderNo = createOrder(buyer, insertProductWithSku("안전모", 30000, true, 5), 1);
		when(paymentGateway.confirm(PAYMENT_KEY, orderNo, 34700)).thenAnswer(invocation -> {
			jdbcTemplate.update("update orders set status = 'EXPIRED' where order_no = ?", orderNo);
			return pg(orderNo, PgStatus.DONE, 34700);
		});
		when(paymentGateway.cancel(anyString(), anyString(), anyString()))
				.thenThrow(new PgUnavailableException("timeout", null));

		confirm(buyer, orderNo, 34700)
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.code").value("PAYMENT_PENDING_CONFIRMATION"));
	}

	@Test
	void PG_결과를_알_수_없으면_확인_중으로_두었다가_보정_작업이_실제_상태에_맞춘다() throws Exception {
		String paidOrder = createOrder(buyer, insertProductWithSku("안전모", 30000, true, 5), 1);
		String unpaidOrder = createOrder(other, insertProductWithSku("안전 조끼", 20000, true, 5), 1);
		when(paymentGateway.confirm(anyString(), anyString(), anyLong()))
				.thenThrow(new PgUnavailableException("timeout", null));
		when(paymentGateway.findByOrderNo(anyString())).thenThrow(new PgUnavailableException("timeout", null));

		confirm(buyer, paidOrder, 34700)
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("PAYMENT_PENDING_CONFIRMATION"));
		confirm(other, unpaidOrder, 24800).andExpect(status().isAccepted());
		assertThat(statusOf(paidOrder)).isEqualTo("PAYMENT_PROCESSING");

		// 확인 중인 주문에 또 승인을 요청하면 거절한다.
		confirm(buyer, paidOrder, 34700)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PAYMENT_IN_PROGRESS"));

		// 아직 5분이 지나지 않은 주문은 보정 대상이 아니다.
		scheduler.reconcileStuckPayments();
		assertThat(statusOf(paidOrder)).isEqualTo("PAYMENT_PROCESSING");

		jdbcTemplate.update("update orders set updated_at = ?", LocalDateTime.now().minusMinutes(10));
		entityManager.clear();

		// PG가 계속 응답하지 않으면 건드리지 않고 다음 주기를 기다린다.
		scheduler.reconcileStuckPayments();
		assertThat(statusOf(paidOrder)).isEqualTo("PAYMENT_PROCESSING");

		// PG 확인 결과: 한 건은 실제로 결제됐고, 한 건은 결제 기록이 없다.
		doReturn(Optional.of(pg(paidOrder, PgStatus.DONE, 34700))).when(paymentGateway).findByOrderNo(paidOrder);
		doReturn(Optional.empty()).when(paymentGateway).findByOrderNo(unpaidOrder);
		scheduler.reconcileStuckPayments();

		assertThat(statusOf(paidOrder)).isEqualTo("PAID");
		assertThat(statusOf(unpaidOrder)).isEqualTo("PENDING");
		assertThat(jdbcTemplate.queryForObject("select count(*) from payment", Long.class)).isEqualTo(1);
	}

	// ───────── 만료 ─────────

	@Test
	void 결제_기한이_지난_주문은_만료되고_잡아_둔_재고가_한_번만_돌아온다() throws Exception {
		long sku = insertProductWithSku("안전모", 30000, true, 5);
		String expiredOrder = createOrder(buyer, sku, 2);
		String freshOrder = createOrder(other, sku, 1);
		assertThat(stockOf(sku)).isEqualTo(2);

		jdbcTemplate.update("update orders set expires_at = ? where order_no = ?",
				LocalDateTime.now().minusMinutes(1), expiredOrder);
		entityManager.clear();

		scheduler.expireUnpaidOrders();
		scheduler.expireUnpaidOrders();

		assertThat(statusOf(expiredOrder)).isEqualTo("EXPIRED");
		assertThat(statusOf(freshOrder)).isEqualTo("PENDING");
		assertThat(stockOf(sku)).isEqualTo(4);

		confirm(buyer, expiredOrder, 64400)
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.code").value("ORDER_EXPIRED"));
		verify(paymentGateway, never()).confirm(anyString(), anyString(), anyLong());
	}

	@Test
	void 가상계좌_보정_작업이_잠금을_잡고_있어도_결제_기한_만료는_처리된다() throws Exception {
		long sku = insertProductWithSku("안전모", 30000, true, 5);
		String expiredOrder = createOrder(buyer, sku, 2);
		jdbcTemplate.update("update orders set expires_at = ? where order_no = ?",
				LocalDateTime.now().minusMinutes(1), expiredOrder);
		// 다른 서버가 PG를 조회하며 가상계좌 보정 잠금을 오래 잡고 있는 상황
		jdbcTemplate.update("""
				update scheduler_lock set lock_until = ?, lock_owner = 'other-instance'
				where lock_name = 'reconcileWaitingDeposits'
				""", LocalDateTime.now().plusMinutes(10));
		entityManager.clear();

		scheduler.expireUnpaidOrders();

		assertThat(statusOf(expiredOrder)).isEqualTo("EXPIRED");
		assertThat(stockOf(sku)).isEqualTo(5);
	}

	@Test
	void 가상계좌_입금_웹훅은_PG를_재조회하고_한번만_입금완료를_반영한다() throws Exception {
		long sku = insertProductWithSku("안전모", 30000, true, 5);
		String orderNo = createOrder(buyer, sku, 1);
		LocalDateTime dueDate = LocalDateTime.now().plusDays(1);
		PgPayment waiting = virtualAccountPg(orderNo, PgStatus.WAITING_FOR_DEPOSIT, 34700, dueDate);
		when(paymentGateway.confirm(PAYMENT_KEY, orderNo, 34700)).thenReturn(waiting);

		confirm(buyer, orderNo, 34700)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("WAITING_DEPOSIT"))
				.andExpect(jsonPath("$.data.payment.virtualAccount.bankCode").value("004"))
				.andExpect(jsonPath("$.data.payment.virtualAccount.accountNumber").value("12345678"))
				.andExpect(jsonPath("$.data.payment.virtualAccount.customerName").value("홍길동"))
				.andExpect(jsonPath("$.data.payment.virtualAccount.dueDate").isNotEmpty())
				.andExpect(content().string(not(containsString("webhook-secret"))));
		assertThat(statusOf(orderNo)).isEqualTo("WAITING_DEPOSIT");
		assertThat(stockOf(sku)).isEqualTo(4);
		assertThat(salesCountOf(sku)).isZero();
		LocalDateTime storedDueDate = jdbcTemplate.queryForObject(
				"select expires_at from orders where order_no = ?", LocalDateTime.class, orderNo);
		assertThat(Duration.between(storedDueDate, dueDate).abs()).isLessThan(Duration.ofMinutes(1));

		PgPayment deposited = virtualAccountPg(orderNo, PgStatus.DONE, 34700, dueDate);
		when(paymentGateway.findByOrderNo(orderNo)).thenReturn(Optional.of(deposited));
		String webhook = """
				{"eventType":"DEPOSIT_CALLBACK","createdAt":"2026-10-18T12:00:00.000000",
				 "data":{"secret":"webhook-secret","status":"DONE","transactionKey":"txn-1","orderId":"%s"}}
				""".formatted(orderNo);

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(post("/api/v1/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(webhook))
					.andExpect(status().isOk());
		}

		assertThat(statusOf(orderNo)).isEqualTo("PAID");
		assertThat(jdbcTemplate.queryForObject("select status from payment", String.class)).isEqualTo("DONE");
		assertThat(stockOf(sku)).isEqualTo(4);
		assertThat(salesCountOf(sku)).isEqualTo(1);
		verify(paymentGateway, times(2)).findByOrderNo(orderNo);
	}

	@Test
	void 가상계좌_웹훅의_secret이_다르면_입금_상태를_바꾸지_않는다() throws Exception {
		String orderNo = createOrder(buyer, insertProductWithSku("안전모", 30000, true, 5), 1);
		LocalDateTime dueDate = LocalDateTime.now().plusDays(1);
		when(paymentGateway.confirm(PAYMENT_KEY, orderNo, 34700))
				.thenReturn(virtualAccountPg(orderNo, PgStatus.WAITING_FOR_DEPOSIT, 34700, dueDate));
		confirm(buyer, orderNo, 34700).andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/payments/webhook").contentType(MediaType.APPLICATION_JSON).content("""
				{"eventType":"DEPOSIT_CALLBACK","data":{"secret":"wrong-secret","status":"DONE","orderId":"%s"}}
				""".formatted(orderNo)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("PAYMENT_WEBHOOK_UNAUTHORIZED"));
		assertThat(statusOf(orderNo)).isEqualTo("WAITING_DEPOSIT");
		verify(paymentGateway, never()).findByOrderNo(anyString());
	}

	@Test
	void 기한이_지난_가상계좌를_PG에서_미입금으로_확인하면_주문을_만료하고_재고를_돌려준다() throws Exception {
		long sku = insertProductWithSku("안전모", 30000, true, 5);
		String orderNo = createOrder(buyer, sku, 1);
		LocalDateTime dueDate = LocalDateTime.now().minusMinutes(1);
		PgPayment waiting = virtualAccountPg(orderNo, PgStatus.WAITING_FOR_DEPOSIT, 34700, dueDate);
		when(paymentGateway.confirm(PAYMENT_KEY, orderNo, 34700)).thenReturn(waiting);
		confirm(buyer, orderNo, 34700).andExpect(status().isOk());
		when(paymentGateway.findByOrderNo(orderNo)).thenReturn(Optional.of(waiting));

		scheduler.reconcileExpiredDeposits();

		assertThat(statusOf(orderNo)).isEqualTo("EXPIRED");
		assertThat(jdbcTemplate.queryForObject("select status from payment", String.class)).isEqualTo("FAILED");
		assertThat(stockOf(sku)).isEqualTo(5);
	}

	@Test
	void 만료_작업이_돌기_전이라도_기한이_지난_주문은_결제할_수_없다() throws Exception {
		String orderNo = createOrder(buyer, insertProductWithSku("안전모", 30000, true, 5), 1);
		jdbcTemplate.update("update orders set expires_at = ?", LocalDateTime.now().minusSeconds(1));
		entityManager.clear();

		confirm(buyer, orderNo, 34700).andExpect(status().isGone());
		verify(paymentGateway, never()).confirm(anyString(), anyString(), anyLong());
	}

	// ───────── 취소 ─────────

	@Test
	void 일반_회원은_관리자_주문_취소를_호출할_수_없다() throws Exception {
		String orderNo = createOrder(buyer, insertProductWithSku("권한 검증 상품", 30000, true, 5), 1);
		mockMvc.perform(post("/api/v1/admin/orders/{orderId}/cancel", orderId(orderNo)).with(member(buyer))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"reason":"고객 요청"}
						"""))
				.andExpect(status().isForbidden());
		assertThat(statusOf(orderNo)).isEqualTo("PENDING");
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 관리자는_결제완료_주문을_환불하고_취소_이력을_남긴다() throws Exception {
		long skuId = insertProductWithSku("관리자 환불 상품", 30000, true, 5);
		String orderNo = payOrder(buyer, skuId, 2);
		long orderId = orderId(orderNo);
		long amount = jdbcTemplate.queryForObject("select total_amount from orders where id = ?", Long.class, orderId);
		when(paymentGateway.cancel(eq(PAYMENT_KEY), eq("관리자 재고 확인"), anyString()))
				.thenReturn(pg(orderNo, PgStatus.CANCELED, amount));

		mockMvc.perform(post("/api/v1/admin/orders/{orderId}/cancel", orderId).with(admin(buyer))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"reason":"관리자 재고 확인"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("CANCELED"))
				.andExpect(jsonPath("$.data.cancelReason").value("관리자 재고 확인"))
				.andExpect(jsonPath("$.data.cancelledByMemberId").value(buyer))
				.andExpect(jsonPath("$.data.cancelledAt").isNotEmpty());

		assertThat(statusOf(orderNo)).isEqualTo("CANCELED");
		assertThat(stockOf(skuId)).isEqualTo(5);
		assertThat(jdbcTemplate.queryForObject("select status from payment where order_id = ?", String.class, orderId))
				.isEqualTo("CANCELED");
		verify(paymentGateway).cancel(eq(PAYMENT_KEY), eq("관리자 재고 확인"), anyString());
		cleanupAdminCancellation(orderId, skuId);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 관리자는_결제대기_주문을_환불없이_만료하고_재고를_복구한다() throws Exception {
		long skuId = insertProductWithSku("관리자 주문 만료 상품", 30000, true, 5);
		String orderNo = createOrder(buyer, skuId, 2);
		long orderId = orderId(orderNo);

		mockMvc.perform(post("/api/v1/admin/orders/{orderId}/cancel", orderId).with(admin(buyer))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"reason":"고객 요청"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("EXPIRED"))
				.andExpect(jsonPath("$.data.cancelReason").value("고객 요청"))
				.andExpect(jsonPath("$.data.items[0].status").value("CANCELED"));

		assertThat(stockOf(skuId)).isEqualTo(5);
		verify(paymentGateway, never()).cancel(anyString(), anyString(), anyString());
		cleanupAdminCancellation(orderId, skuId);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 관리자는_가상계좌_입금대기_주문을_PG에서_취소한_뒤_만료한다() throws Exception {
		long skuId = insertProductWithSku("가상계좌 관리자 취소 상품", 30000, true, 5);
		String orderNo = createOrder(buyer, skuId, 1);
		long amount = jdbcTemplate.queryForObject("select total_amount from orders where order_no = ?", Long.class,
				orderNo);
		when(paymentGateway.confirm(PAYMENT_KEY, orderNo, amount)).thenReturn(
				virtualAccountPg(orderNo, PgStatus.WAITING_FOR_DEPOSIT, amount, LocalDateTime.now().plusDays(1)));
		confirm(buyer, orderNo, amount).andExpect(status().isOk());
		long orderId = orderId(orderNo);
		when(paymentGateway.cancel(eq(PAYMENT_KEY), eq("가상계좌 취소"), anyString()))
				.thenReturn(pg(orderNo, PgStatus.CANCELED, amount));

		mockMvc.perform(post("/api/v1/admin/orders/{orderId}/cancel", orderId).with(admin(buyer))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"reason":"가상계좌 취소"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("EXPIRED"))
				.andExpect(jsonPath("$.data.payment.status").value("FAILED"));

		assertThat(stockOf(skuId)).isEqualTo(5);
		verify(paymentGateway).cancel(eq(PAYMENT_KEY), eq("가상계좌 취소"), anyString());
		cleanupAdminCancellation(orderId, skuId);
	}

	@Test
	void 결제한_주문을_취소하면_PG에서_환불하고_재고를_되돌리며_두_번_취소되지_않는다() throws Exception {
		long sku = insertProductWithSku("안전모", 30000, true, 5);
		String orderNo = payOrder(buyer, sku, 2);
		assertThat(stockOf(sku)).isEqualTo(3);
		assertThat(salesCountOf(sku)).isEqualTo(2);
		when(paymentGateway.cancel(eq(PAYMENT_KEY), anyString(), anyString()))
				.thenReturn(pg(orderNo, PgStatus.CANCELED, 64400));

		mockMvc.perform(post("/api/v1/orders/{orderNo}/cancel", orderNo).with(member(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("CANCELED"))
				.andExpect(jsonPath("$.data.payment.status").value("CANCELED"))
				.andExpect(jsonPath("$.data.payment.balanceAmount").value(0))
				.andExpect(jsonPath("$.data.items[0].status").value("CANCELED"));
		assertThat(stockOf(sku)).isEqualTo(5);
		assertThat(salesCountOf(sku)).isZero();

		mockMvc.perform(post("/api/v1/orders/{orderNo}/cancel", orderNo).with(member(buyer)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CANCEL_NOT_ALLOWED"));
		assertThat(stockOf(sku)).isEqualTo(5);
		verify(paymentGateway, times(1)).cancel(anyString(), anyString(), anyString());
	}

	@Test
	void PG_환불_응답이_없으면_취소_중으로_유지하고_재고는_보정_전까지_그대로_둔다() throws Exception {
		long sku = insertProductWithSku("안전모", 30000, true, 5);
		String orderNo = payOrder(buyer, sku, 2);
		when(paymentGateway.cancel(anyString(), anyString(), anyString()))
				.thenThrow(new PgUnavailableException("timeout", null));
		when(paymentGateway.findByOrderNo(orderNo)).thenReturn(Optional.of(pg(orderNo, PgStatus.DONE, 64400)));

		mockMvc.perform(post("/api/v1/orders/{orderNo}/cancel", orderNo).with(member(buyer)))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.code").value("CANCEL_PENDING_CONFIRMATION"));

		assertThat(statusOf(orderNo)).isEqualTo("CANCEL_REQUESTED");
		assertThat(stockOf(sku)).isEqualTo(3);
		assertThat(salesCountOf(sku)).isEqualTo(2);
	}

	@Test
	void PG_취소_타임아웃_후_보정_작업이_같은_멱등키로_재시도하고_재고를_한번만_복구한다() throws Exception {
		long sku = insertProductWithSku("안전모", 30000, true, 5);
		String orderNo = payOrder(buyer, sku, 2);
		when(paymentGateway.cancel(anyString(), anyString(), anyString()))
				.thenThrow(new PgUnavailableException("timeout", null))
				.thenReturn(pg(orderNo, PgStatus.CANCELED, 64400));
		when(paymentGateway.findByOrderNo(orderNo)).thenReturn(Optional.of(pg(orderNo, PgStatus.DONE, 64400)));

		mockMvc.perform(post("/api/v1/orders/{orderNo}/cancel", orderNo).with(member(buyer)))
				.andExpect(status().isAccepted());
		assertThat(statusOf(orderNo)).isEqualTo("CANCEL_REQUESTED");
		assertThat(stockOf(sku)).isEqualTo(3);

		jdbcTemplate.update("update orders set updated_at = ? where order_no = ?",
				LocalDateTime.now().minusMinutes(10), orderNo);
		entityManager.clear();
		doReturn(Optional.of(pg(orderNo, PgStatus.DONE, 64400))).when(paymentGateway).findByOrderNo(orderNo);

		scheduler.reconcileStuckCancellations();
		scheduler.reconcileStuckCancellations();

		assertThat(statusOf(orderNo)).isEqualTo("CANCELED");
		assertThat(stockOf(sku)).isEqualTo(5);
		assertThat(salesCountOf(sku)).isZero();
		ArgumentCaptor<String> idempotencyKey = ArgumentCaptor.forClass(String.class);
		verify(paymentGateway, times(2)).cancel(eq(PAYMENT_KEY), anyString(), idempotencyKey.capture());
		assertThat(idempotencyKey.getAllValues()).hasSize(2).allMatch(key -> key.startsWith("cancel-"));
		assertThat(idempotencyKey.getAllValues().get(1)).isEqualTo(idempotencyKey.getAllValues().get(0));
	}

	@Test
	void 과거_CANCELED_Done_불일치도_PG를_조회하고_전액환불_확인_후_재고를_복구한다() throws Exception {
		long sku = insertProductWithSku("안전모", 30000, true, 5);
		String orderNo = payOrder(buyer, sku, 2);
		jdbcTemplate.update("update orders set status = 'CANCELED', updated_at = ? where order_no = ?",
				LocalDateTime.now().minusMinutes(10), orderNo);
		entityManager.clear();
		doReturn(Optional.of(pg(orderNo, PgStatus.CANCELED, 64400))).when(paymentGateway).findByOrderNo(orderNo);

		scheduler.reconcileStuckCancellations();

		assertThat(statusOf(orderNo)).isEqualTo("CANCELED");
		assertThat(jdbcTemplate.queryForObject("select status from payment", String.class)).isEqualTo("CANCELED");
		assertThat(stockOf(sku)).isEqualTo(5);
		assertThat(salesCountOf(sku)).isZero();
	}

	@Test
	void 과거_CANCELED_Done_불일치에서_PG가_미취소면_기존_멱등키로_재요청한다() throws Exception {
		long sku = insertProductWithSku("안전모", 30000, true, 5);
		String orderNo = payOrder(buyer, sku, 2);
		jdbcTemplate.update("update orders set status = 'CANCELED', updated_at = ? where order_no = ?",
				LocalDateTime.now().minusMinutes(10), orderNo);
		entityManager.clear();
		doReturn(Optional.of(pg(orderNo, PgStatus.DONE, 64400))).when(paymentGateway).findByOrderNo(orderNo);
		when(paymentGateway.cancel(PAYMENT_KEY, "고객 주문 취소", "cancel-" + orderNo))
				.thenReturn(pg(orderNo, PgStatus.CANCELED, 64400));

		scheduler.reconcileStuckCancellations();

		assertThat(statusOf(orderNo)).isEqualTo("CANCELED");
		assertThat(jdbcTemplate.queryForObject("select status from payment", String.class)).isEqualTo("CANCELED");
		assertThat(stockOf(sku)).isEqualTo(5);
		assertThat(salesCountOf(sku)).isZero();
		verify(paymentGateway).cancel(PAYMENT_KEY, "고객 주문 취소", "cancel-" + orderNo);
	}

	@Test
	void 멱등키_유효기간을_넘긴_미취소_주문은_자동으로_재요청하지_않는다() throws Exception {
		long sku = insertProductWithSku("안전모", 30000, true, 5);
		String orderNo = payOrder(buyer, sku, 2);
		jdbcTemplate.update("update orders set status = 'CANCELED', updated_at = ? where order_no = ?",
				LocalDateTime.now().minusDays(20), orderNo);
		entityManager.clear();
		doReturn(Optional.of(pg(orderNo, PgStatus.DONE, 64400))).when(paymentGateway).findByOrderNo(orderNo);

		scheduler.reconcileStuckCancellations();

		assertThat(statusOf(orderNo)).isEqualTo("CANCELED");
		assertThat(jdbcTemplate.queryForObject("select status from payment", String.class)).isEqualTo("DONE");
		assertThat(stockOf(sku)).isEqualTo(3);
		verify(paymentGateway, never()).cancel(anyString(), anyString(), anyString());
	}

	@Test
	void 결제_전이거나_배송이_시작된_주문은_취소할_수_없다() throws Exception {
		long sku = insertProductWithSku("안전모", 30000, true, 9);
		String unpaid = createOrder(buyer, sku, 1);
		mockMvc.perform(post("/api/v1/orders/{orderNo}/cancel", unpaid).with(member(buyer)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CANCEL_NOT_ALLOWED"));

		String shipped = payOrder(other, sku, 1);
		jdbcTemplate.update("update orders set status = 'SHIPPED' where order_no = ?", shipped);
		entityManager.clear();
		mockMvc.perform(post("/api/v1/orders/{orderNo}/cancel", shipped).with(member(other)))
				.andExpect(status().isConflict());

		verify(paymentGateway, never()).cancel(anyString(), anyString(), anyString());
	}

	// ───────── FK 제약 ─────────

	@Test
	void 없는_회원이나_옵션이나_주문을_가리키는_행은_DB가_거부한다() throws Exception {
		String orderNo = createOrder(buyer, insertProductWithSku("안전모", 30000, true, 5), 1);
		Long orderId = jdbcTemplate.queryForObject("select id from orders where order_no = ?", Long.class, orderNo);

		// orders.member_id → member.id
		assertThatThrownBy(() -> jdbcTemplate.update("""
				insert into orders (order_no, member_id, status, total_amount, item_amount, discount_amount,
				                    shipping_fee, order_name, expires_at, version, created_at, updated_at)
				values ('NO-SUCH-MEMBER', 999999, 'PENDING', 1000, 1000, 0, 0, '주문', now(), 0, now(), now())
				""")).isInstanceOf(DataIntegrityViolationException.class);
		// order_item.sku_id → product_sku.id
		assertThatThrownBy(() -> jdbcTemplate.update("""
				insert into order_item (order_id, sku_id, product_name, option_name, order_price, quantity, status)
				values (?, 999999, '상품', '옵션', 1000, 1, 'ORDERED')
				""", orderId)).isInstanceOf(DataIntegrityViolationException.class);
		// payment.order_id → orders.id
		assertThatThrownBy(() -> jdbcTemplate.update("""
				insert into payment (order_id, payment_key, method, status, approved_amount, balance_amount,
				                     created_at, updated_at)
				values (999999, 'no-such-order', 'CARD', 'DONE', 1000, 1000, now(), now())
				""")).isInstanceOf(DataIntegrityViolationException.class);
	}

	// ───────── 동시성 ─────────

	/** 실제로 여러 스레드가 같은 옵션의 재고를 동시에 차감해도 재고보다 많이 팔리지 않는지 확인한다. */
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 재고_5개에_20명이_동시에_주문해도_5명만_성공한다() throws Exception {
		long skuId = transactionTemplate.execute(status -> insertProductWithSku("동시성 테스트 상품", 1000, true, 5));
		try {
			int buyers = 20;
			ExecutorService pool = Executors.newFixedThreadPool(buyers);
			CountDownLatch start = new CountDownLatch(1);
			CountDownLatch done = new CountDownLatch(buyers);
			AtomicInteger succeeded = new AtomicInteger();

			for (int i = 0; i < buyers; i++) {
				pool.submit(() -> {
					try {
						start.await();
						int updated = transactionTemplate.execute(status -> productSkuRepository.decreaseStock(skuId, 1));
						succeeded.addAndGet(updated);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} finally {
						done.countDown();
					}
				});
			}
			start.countDown();
			assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
			pool.shutdown();

			assertThat(succeeded.get()).isEqualTo(5);
			assertThat(stockOf(skuId)).isZero();
		} finally {
			// 이 테스트는 트랜잭션 없이 돌아 자동으로 되돌려지지 않으므로 만든 데이터를 직접 지운다.
			jdbcTemplate.update("delete from product_sku where id = ?", skuId);
			jdbcTemplate.update("delete from product where name = '동시성 테스트 상품'");
			jdbcTemplate.update("delete from member where id in (?, ?)", buyer, other);
		}
	}

	// ───────── 도우미 ─────────

	private RequestPostProcessor member(long memberId) {
		return jwt().jwt(token -> token.subject(String.valueOf(memberId)))
				.authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	private RequestPostProcessor admin(long memberId) {
		return jwt().jwt(token -> token.subject(String.valueOf(memberId)))
				.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	private long orderId(String orderNo) {
		return jdbcTemplate.queryForObject("select id from orders where order_no = ?", Long.class, orderNo);
	}

	private void cleanupAdminCancellation(long orderId, long skuId) {
		Long productId = jdbcTemplate.queryForObject("select product_id from product_sku where id = ?", Long.class, skuId);
		jdbcTemplate.update("delete from cart_item where member_id in (?, ?)", buyer, other);
		jdbcTemplate.update("delete from payment where order_id = ?", orderId);
		jdbcTemplate.update("delete from order_item where order_id = ?", orderId);
		jdbcTemplate.update("delete from orders where id = ?", orderId);
		jdbcTemplate.update("delete from product_sku where id = ?", skuId);
		jdbcTemplate.update("delete from product where id = ?", productId);
		jdbcTemplate.update("delete from member where id in (?, ?)", buyer, other);
	}

	private PgPayment pg(String orderNo, PgStatus status, long amount) {
		long balance = status == PgStatus.CANCELED ? 0 : amount;
		return new PgPayment(PAYMENT_KEY, orderNo, status, amount, balance, "CARD", LocalDateTime.now(), "{}");
	}

	private PgPayment virtualAccountPg(String orderNo, PgStatus status, long amount, LocalDateTime dueDate) {
		long balance = status == PgStatus.CANCELED ? 0 : amount;
		return new PgPayment(PAYMENT_KEY, orderNo, status, amount, balance, "가상계좌", LocalDateTime.now(), "{}",
				new PgVirtualAccount("004", "12345678", "홍길동", dueDate), "webhook-secret");
	}

	private void addToCart(long memberId, long skuId, int quantity) throws Exception {
		mockMvc.perform(post("/api/v1/cart/items").with(member(memberId)).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"skuId":%d,"quantity":%d}
								""".formatted(skuId, quantity)))
				.andExpect(status().isOk());
	}

	private void saveDelivery(long memberId) throws Exception {
		mockMvc.perform(post("/api/v1/checkout/delivery").with(member(memberId))
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"recipient":"홍길동","recipientPhone":"010-1234-5678","zipCode":"06236",
								 "address":"서울시 강남구 테헤란로 1","addressDetail":"101호","deliveryRequest":"문 앞"}
								"""))
				.andExpect(status().isOk());
	}

	/** 장바구니 담기 → 배송지 저장 → 주문 생성까지 하고 주문번호를 돌려준다. */
	private String createOrder(long memberId, long skuId, int quantity) throws Exception {
		addToCart(memberId, skuId, quantity);
		saveDelivery(memberId);
		String body = mockMvc.perform(post("/api/v1/orders").with(member(memberId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.orderNo");
	}

	/** 주문을 만들고 결제 승인까지 마친다. */
	private String payOrder(long memberId, long skuId, int quantity) throws Exception {
		String orderNo = createOrder(memberId, skuId, quantity);
		long amount = jdbcTemplate.queryForObject("select total_amount from orders where order_no = ?", Long.class,
				orderNo);
		when(paymentGateway.confirm(PAYMENT_KEY, orderNo, amount)).thenReturn(pg(orderNo, PgStatus.DONE, amount));
		confirm(memberId, orderNo, amount).andExpect(status().isOk());
		return orderNo;
	}

	private org.springframework.test.web.servlet.ResultActions confirm(long memberId, String orderNo, long amount)
			throws Exception {
		return mockMvc.perform(post("/api/v1/payments/confirm").with(member(memberId))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"paymentKey":"%s","orderId":"%s","amount":%d}
						""".formatted(PAYMENT_KEY, orderNo, amount)));
	}

	private long insertMember() {
		String email = UUID.randomUUID() + "@example.com";
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values (?, 'not-used', '구매자', 'USER', 'ACTIVE', now(), now())
				""", email);
		return jdbcTemplate.queryForObject("select id from member where email = ?", Long.class, email);
	}

	private long insertProductWithSku(String name, long price, boolean priceVisible, int stock) {
		jdbcTemplate.update("""
				insert into product (name, base_price, price_visible, status, created_at, updated_at)
				values (?, ?, ?, 'ON_SALE', now(), now())
				""", name, price, priceVisible);
		Long productId = jdbcTemplate.queryForObject("select id from product where name = ?", Long.class, name);
		jdbcTemplate.update("""
				insert into product_sku (product_id, option_name, price, stock, version) values (?, '기본', ?, ?, 0)
				""", productId, price, stock);
		return jdbcTemplate.queryForObject("select id from product_sku where product_id = ?", Long.class, productId);
	}

	private int stockOf(long skuId) {
		return jdbcTemplate.queryForObject("select stock from product_sku where id = ?", Integer.class, skuId);
	}

	/** 옵션이 속한 상품의 누적 판매 수량 */
	private long salesCountOf(long skuId) {
		return jdbcTemplate.queryForObject("""
				select p.sales_count from product p join product_sku s on s.product_id = p.id where s.id = ?
				""", Long.class, skuId);
	}

	private String statusOf(String orderNo) {
		return jdbcTemplate.queryForObject("select status from orders where order_no = ?", String.class, orderNo);
	}
}
