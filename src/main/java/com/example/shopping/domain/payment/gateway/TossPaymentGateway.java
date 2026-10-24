package com.example.shopping.domain.payment.gateway;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.shopping.domain.payment.gateway.PgPayment.PgStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** 토스페이먼츠 결제 API 연동. 시크릿 키를 Basic 인증으로 보낸다. */
@Slf4j
public class TossPaymentGateway implements PaymentGateway {

	private static final ZoneId BUSINESS_TIME_ZONE = ZoneId.of("Asia/Seoul");

	private final RestClient restClient;
	private final TossProperties properties;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public TossPaymentGateway(RestClient restClient, TossProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public PgPayment confirm(String paymentKey, String orderNo, long amount) {
		return call(HttpMethod.POST, "/v1/payments/confirm", orderNo,
				Map.of("paymentKey", paymentKey, "orderId", orderNo, "amount", amount));
	}

	@Override
	public Optional<PgPayment> findByOrderNo(String orderNo) {
		try {
			return Optional.of(call(HttpMethod.GET, "/v1/payments/orders/{orderId}", null, null, orderNo));
		} catch (PgRejectedException e) {
			if (e.getCause() instanceof HttpClientErrorException.NotFound) {
				// 해당 주문번호로 시작된 결제가 없다.
				return Optional.empty();
			}
			// "결제가 없다"고 확인된 것이 아니므로 없다고 단정하지 않는다.
			throw new PgUnavailableException("토스페이먼츠 결제 조회 실패: " + e.getCode(), e);
		}
	}

	@Override
	public PgPayment cancel(String paymentKey, String reason, String idempotencyKey) {
		return call(HttpMethod.POST, "/v1/payments/{paymentKey}/cancel", idempotencyKey,
				Map.of("cancelReason", reason), paymentKey);
	}

	private PgPayment call(HttpMethod method, String uri, String idempotencyKey, Map<String, Object> body,
			Object... uriVariables) {
		if (!StringUtils.hasText(properties.secretKey())) {
			// PG를 부르지도 않았으므로 "결과를 알 수 없음"이 아니라 확실한 실패다. (고객에게 '확인 중'으로 안내하지 않는다.)
			log.error("토스페이먼츠 시크릿 키(TOSS_SECRET_KEY)가 설정되지 않아 결제를 처리할 수 없습니다.");
			throw new PgRejectedException("PG_NOT_CONFIGURED", null);
		}
		String basicAuth = Base64.getEncoder()
				.encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
		try {
			RestClient.RequestBodySpec request = restClient.method(method)
					.uri(uri, uriVariables)
					.header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth);
			if (idempotencyKey != null) {
				request.header("Idempotency-Key", idempotencyKey);
			}
			if (body != null) {
				request.contentType(MediaType.APPLICATION_JSON).body(jsonMapper.writeValueAsString(body));
			}
			return toPayment(request.retrieve().body(String.class));
		} catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
			// 시크릿 키가 잘못된 경우. 결제 자체가 거절된 것이 아니므로 "결과 불명"으로 다룬다.
			throw new PgUnavailableException("토스페이먼츠 인증 실패. 시크릿 키를 확인하세요.", e);
		} catch (HttpClientErrorException e) {
			throw new PgRejectedException(errorCode(e.getResponseBodyAsString()), e);
		} catch (RestClientException | JacksonException e) {
			// 5xx, 타임아웃, 연결 실패, 해석할 수 없는 응답: 결제가 됐는지 안 됐는지 알 수 없다.
			throw new PgUnavailableException("토스페이먼츠 호출 실패: " + e.getClass().getSimpleName(), e);
		}
	}

	private PgPayment toPayment(String raw) {
		if (raw == null) {
			throw new PgUnavailableException("토스페이먼츠가 빈 응답을 보냈습니다.", null);
		}
		TossPayment payment = jsonMapper.readValue(raw, TossPayment.class);
		PgStatus status = switch (String.valueOf(payment.status())) {
			case "DONE" -> PgStatus.DONE;
			case "WAITING_FOR_DEPOSIT" -> PgStatus.WAITING_FOR_DEPOSIT;
			case "CANCELED" -> PgStatus.CANCELED;
			default -> PgStatus.OTHER;
		};
		return new PgPayment(payment.paymentKey(), payment.orderId(), status,
				payment.totalAmount() == null ? 0 : payment.totalAmount(),
				payment.balanceAmount() == null ? 0 : payment.balanceAmount(),
				payment.method(),
				payment.approvedAt() == null ? null
						: OffsetDateTime.parse(payment.approvedAt())
								.atZoneSameInstant(BUSINESS_TIME_ZONE)
								.toLocalDateTime(),
				raw, toVirtualAccount(payment.virtualAccount()), payment.secret());
	}

	private PgVirtualAccount toVirtualAccount(TossVirtualAccount virtualAccount) {
		if (virtualAccount == null) {
			return null;
		}
		try {
			return new PgVirtualAccount(virtualAccount.bankCode(), virtualAccount.accountNumber(),
					virtualAccount.customerName(), virtualAccount.dueDate() == null
							? null : LocalDateTime.parse(virtualAccount.dueDate()));
		} catch (java.time.DateTimeException e) {
			throw new PgUnavailableException("토스페이먼츠 가상계좌 입금 기한을 해석할 수 없습니다.", e);
		}
	}

	private String errorCode(String responseBody) {
		try {
			TossError error = jsonMapper.readValue(responseBody, TossError.class);
			return error.code() == null ? "UNKNOWN" : error.code();
		} catch (JacksonException e) {
			return "UNKNOWN";
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TossPayment(String paymentKey, String orderId, String status, Long totalAmount, Long balanceAmount,
			String method, String approvedAt, TossVirtualAccount virtualAccount, String secret) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TossVirtualAccount(String bankCode, String accountNumber, String customerName, String dueDate) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TossError(String code, String message) {
	}
}
