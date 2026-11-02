package com.example.shopping.domain.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.order.dto.request.ShippingPolicyRequest;
import com.example.shopping.domain.order.dto.response.ShippingPolicyResponse;
import com.example.shopping.domain.order.entity.ShippingPolicy;
import com.example.shopping.domain.order.repository.ShippingPolicyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShippingPolicyService {

	private final ShippingPolicyRepository shippingPolicyRepository;

	/** 지금 적용하는 배송비 정책. 관리자가 아직 정하지 않았으면 기본값 */
	public ShippingPolicy current() {
		return shippingPolicyRepository.findById(ShippingPolicy.ID).orElseGet(ShippingPolicy::defaults);
	}

	public ShippingPolicyResponse get() {
		return ShippingPolicyResponse.from(current());
	}

	/** 이후 장바구니 금액과 새 주문에 적용한다. 이미 만든 주문의 금액은 바뀌지 않는다. */
	@Transactional
	public ShippingPolicyResponse update(ShippingPolicyRequest request) {
		ShippingPolicy policy = current();
		policy.change(request.shippingFee(), request.freeShippingThreshold());
		return ShippingPolicyResponse.from(shippingPolicyRepository.saveAndFlush(policy));
	}
}
