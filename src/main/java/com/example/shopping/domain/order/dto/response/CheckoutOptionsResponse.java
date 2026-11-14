package com.example.shopping.domain.order.dto.response;

import java.util.List;

import com.example.shopping.domain.order.entity.ShippingPolicy;

/**
 * 주문서 화면의 선택지. 배송비는 관리자 배송비 정책을 따르고, 나머지는 서버가 처리할 수 있는 값이다.
 *
 * @param shippingFee           기본 배송비
 * @param freeShippingThreshold 주문 상품 금액이 이 값 이상이면 무료배송. null이면 무료배송 없음
 * @param freeShippingScope     무료배송 기준을 적용하는 범위. 서버는 한 주문의 상품 금액 합계로 판단한다(cart).
 * @param baseFeeMode           배송비 부담. 서버는 배송비를 고객에게 받는다(customer).
 * @param deliveryRequests      배송 요청 문구. 주문할 때 고른 문구(또는 직접 쓴 글)를 배송 요청 사항(deliveryRequest)으로 보낸다.
 * @param shippingMethods       배송 방법
 * @param paymentMethods        결제 수단
 */
public record CheckoutOptionsResponse(long shippingFee, Long freeShippingThreshold, String freeShippingScope,
		String baseFeeMode, List<Option> deliveryRequests, List<ShippingMethod> shippingMethods,
		List<Option> paymentMethods) {

	/** 배송 요청 문구. 직접 입력은 화면이 따로 받는다. */
	static final List<Option> DELIVERY_REQUESTS = List.of(
			new Option("call", "배송 전에 연락 바랍니다"),
			new Option("door", "문 앞에 놓아 주세요"),
			new Option("security", "경비실에 맡겨 주세요"),
			new Option("site-office", "현장 사무실로 배송해 주세요"));

	static final List<ShippingMethod> SHIPPING_METHODS = List.of(
			new ShippingMethod("standard", "일반 배송", "결제 후 영업일 기준 2~3일"));

	/** 토스페이먼츠로 결제하고 서버가 승인·입금 알림(가상계좌)을 처리하는 수단 */
	static final List<Option> PAYMENT_METHODS = List.of(
			new Option("creditCard", "신용카드"),
			new Option("bankTransfer", "계좌이체"),
			new Option("virtualAccount", "가상계좌"),
			new Option("mobile", "휴대폰 결제"));

	public record Option(String id, String label) {
	}

	public record ShippingMethod(String id, String label, String description) {
	}

	public static CheckoutOptionsResponse of(ShippingPolicy policy) {
		return new CheckoutOptionsResponse(policy.getShippingFee(), policy.getFreeShippingThreshold(), "cart",
				"customer", DELIVERY_REQUESTS, SHIPPING_METHODS, PAYMENT_METHODS);
	}
}
