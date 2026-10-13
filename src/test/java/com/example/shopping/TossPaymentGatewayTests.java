package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.example.shopping.domain.payment.gateway.PgPayment;
import com.example.shopping.domain.payment.gateway.PgPayment.PgStatus;
import com.example.shopping.domain.payment.gateway.PgRejectedException;
import com.example.shopping.domain.payment.gateway.PgUnavailableException;
import com.example.shopping.domain.payment.gateway.TossPaymentGateway;
import com.example.shopping.domain.payment.gateway.TossProperties;

/**
 * 토스페이먼츠로 보내는 요청 형식과 응답 해석을 가짜 HTTP 서버로 확인한다.
 * 실제 토스 서버와 통신하는 테스트는 아니므로, 운영 전에 테스트 키로 실제 결제를 한 번 해 봐야 한다.
 */
class TossPaymentGatewayTests {

	private static final String BASE_URL = "https://api.tosspayments.com";
	private static final String SECRET_KEY = "test_sk_dummy";
	private static final String BASIC_AUTH = "Basic "
			+ Base64.getEncoder().encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8));

	private MockRestServiceServer server;
	private TossPaymentGateway gateway;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		gateway = new TossPaymentGateway(builder.build(), new TossProperties(SECRET_KEY, BASE_URL));
	}

	@Test
	void 승인_요청은_시크릿_키와_멱등_키를_실어_보내고_응답을_해석한다() {
		server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
				.andExpect(header("Idempotency-Key", "ORDER-1"))
				.andExpect(content().json("""
						{"paymentKey":"pay-key","orderId":"ORDER-1","amount":60000}
						"""))
				.andRespond(withSuccess("""
						{"paymentKey":"pay-key","orderId":"ORDER-1","status":"DONE","totalAmount":60000,
						 "balanceAmount":60000,"method":"카드","approvedAt":"2026-10-13T10:16:02+09:00",
						 "card":{"issuerCode":"61"}}
						""", MediaType.APPLICATION_JSON));

		PgPayment payment = gateway.confirm("pay-key", "ORDER-1", 60000);

		assertThat(payment.status()).isEqualTo(PgStatus.DONE);
		assertThat(payment.isApproved()).isTrue();
		assertThat(payment.totalAmount()).isEqualTo(60000);
		assertThat(payment.method()).isEqualTo("카드");
		assertThat(payment.approvedAt()).isNotNull();
		assertThat(payment.raw()).contains("issuerCode");
		server.verify();
	}

	@Test
	void PG가_4xx로_거절하면_에러_코드와_함께_거절_예외를_던진다() {
		server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST)
						.contentType(MediaType.APPLICATION_JSON)
						.body("""
								{"code":"REJECT_CARD_COMPANY","message":"결제 승인이 거절되었습니다"}
								"""));

		assertThatThrownBy(() -> gateway.confirm("pay-key", "ORDER-1", 60000))
				.isInstanceOfSatisfying(PgRejectedException.class,
						e -> assertThat(e.getCode()).isEqualTo("REJECT_CARD_COMPANY"));
	}

	@Test
	void PG_장애나_인증_실패는_거절이_아니라_결과_불명으로_다룬다() {
		server.expect(requestTo(BASE_URL + "/v1/payments/confirm")).andRespond(withServerError());
		assertThatThrownBy(() -> gateway.confirm("pay-key", "ORDER-1", 60000))
				.isInstanceOf(PgUnavailableException.class);

		server.reset();
		server.expect(requestTo(BASE_URL + "/v1/payments/confirm")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
		assertThatThrownBy(() -> gateway.confirm("pay-key", "ORDER-1", 60000))
				.isInstanceOf(PgUnavailableException.class);

		server.reset();
		server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
				.andRespond(withSuccess("이건 JSON이 아니다", MediaType.TEXT_PLAIN));
		assertThatThrownBy(() -> gateway.confirm("pay-key", "ORDER-1", 60000))
				.isInstanceOf(PgUnavailableException.class);
	}

	@Test
	void 주문번호로_조회했을_때_결제_기록이_없으면_빈_값이고_그_밖의_거절은_없다고_단정하지_않는다() {
		server.expect(requestTo(BASE_URL + "/v1/payments/orders/ORDER-1"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
				.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
						.body("""
								{"code":"NOT_FOUND_PAYMENT","message":"존재하지 않는 결제 입니다."}
								"""));
		assertThat(gateway.findByOrderNo("ORDER-1")).isEmpty();

		server.reset();
		server.expect(requestTo(BASE_URL + "/v1/payments/orders/ORDER-1"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
						.body("""
								{"code":"INVALID_REQUEST","message":"잘못된 요청입니다."}
								"""));
		assertThatThrownBy(() -> gateway.findByOrderNo("ORDER-1")).isInstanceOf(PgUnavailableException.class);

		server.reset();
		server.expect(requestTo(BASE_URL + "/v1/payments/orders/ORDER-1"))
				.andRespond(withSuccess("""
						{"paymentKey":"pay-key","orderId":"ORDER-1","status":"WAITING_FOR_DEPOSIT","totalAmount":1000,
						 "balanceAmount":1000,"method":"가상계좌","approvedAt":null}
						""", MediaType.APPLICATION_JSON));
		assertThat(gateway.findByOrderNo("ORDER-1")).hasValueSatisfying(payment -> {
			assertThat(payment.status()).isEqualTo(PgStatus.WAITING_FOR_DEPOSIT);
			assertThat(payment.approvedAt()).isNull();
		});
	}

	@Test
	void 취소_요청은_결제_키_경로로_사유와_멱등_키를_보낸다() {
		server.expect(requestTo(BASE_URL + "/v1/payments/pay-key/cancel"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Idempotency-Key", "cancel-ORDER-1"))
				.andExpect(content().json("""
						{"cancelReason":"고객 주문 취소"}
						"""))
				.andRespond(withSuccess("""
						{"paymentKey":"pay-key","orderId":"ORDER-1","status":"CANCELED","totalAmount":60000,
						 "balanceAmount":0,"method":"카드"}
						""", MediaType.APPLICATION_JSON));

		PgPayment payment = gateway.cancel("pay-key", "고객 주문 취소", "cancel-ORDER-1");

		assertThat(payment.status()).isEqualTo(PgStatus.CANCELED);
		assertThat(payment.balanceAmount()).isZero();
		server.verify();
	}

	@Test
	void 시크릿_키가_없으면_PG를_호출하지_않고_확실한_실패로_다룬다() {
		TossPaymentGateway unconfigured = new TossPaymentGateway(RestClient.builder().baseUrl(BASE_URL).build(),
				new TossProperties("", BASE_URL));

		// 승인·취소는 아무 일도 일어나지 않았으므로 실패가 확실하다.
		assertThatThrownBy(() -> unconfigured.confirm("pay-key", "ORDER-1", 60000))
				.isInstanceOfSatisfying(PgRejectedException.class,
						e -> assertThat(e.getCode()).isEqualTo("PG_NOT_CONFIGURED"));
		// 조회는 "결제 기록이 없다"고 답하면 안 된다 (실제로는 결제됐을 수 있다).
		assertThatThrownBy(() -> unconfigured.findByOrderNo("ORDER-1")).isInstanceOf(PgUnavailableException.class);
	}
}
