package com.example.shopping.domain.member.dto.response;

import com.example.shopping.domain.member.entity.MemberAddress;

/**
 * 저장한 배송지. 필드 이름은 주문 요청의 배송지(delivery)와 같다.
 *
 * @param id             배송지 ID
 * @param label          배송지 이름(예: 회사)
 * @param recipient      받는 분 이름
 * @param recipientPhone 받는 분 연락처
 * @param zipCode        우편번호
 * @param address        주소
 * @param addressDetail  상세 주소
 * @param isDefault      기본 배송지인지
 */
public record MemberAddressResponse(
		Long id,
		String label,
		String recipient,
		String recipientPhone,
		String zipCode,
		String address,
		String addressDetail,
		boolean isDefault) {

	public static MemberAddressResponse from(MemberAddress address) {
		return new MemberAddressResponse(address.getId(), address.getLabel(), address.getRecipient(),
				address.getRecipientPhone(), address.getZipCode(), address.getAddress(), address.getAddressDetail(),
				address.isDefaultAddress());
	}
}
