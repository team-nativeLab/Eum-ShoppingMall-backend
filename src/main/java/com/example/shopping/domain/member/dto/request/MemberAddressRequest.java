package com.example.shopping.domain.member.dto.request;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 배송지 등록·수정. 필드 이름은 주문 요청의 배송지(delivery)와 같아, 고른 배송지를 주문서에 그대로 넣을 수 있다.
 *
 * @param label          배송지 이름(예: 회사)
 * @param recipient      받는 분 이름(50자 이하)
 * @param recipientPhone 받는 분 연락처(예: 010-1234-5678, 하이픈은 생략 가능)
 * @param zipCode        우편번호(숫자 5자리)
 * @param address        주소(200자 이하)
 * @param addressDetail  상세 주소(200자 이하). 없으면 생략
 * @param isDefault      true면 기본 배송지로 정한다. 처음 등록하는 배송지는 값과 상관없이 기본 배송지가 된다.
 */
public record MemberAddressRequest(
		@NotBlank(message = "배송지 이름을 입력해 주세요.")
		@Size(max = 30, message = "배송지 이름은 30자 이하여야 합니다.")
		String label,

		@NotBlank(message = "받는 분 이름을 입력해 주세요.")
		@Size(max = 50, message = "받는 분 이름은 50자 이하여야 합니다.")
		String recipient,

		@NotBlank(message = "연락처를 입력해 주세요.")
		@Pattern(regexp = ValidationPatterns.PHONE, message = "연락처 형식이 올바르지 않습니다.")
		String recipientPhone,

		@NotBlank(message = "우편번호를 입력해 주세요.")
		@Pattern(regexp = "^[0-9]{5}$", message = "우편번호는 숫자 5자리여야 합니다.")
		String zipCode,

		@NotBlank(message = "주소를 입력해 주세요.")
		@Size(max = 200, message = "주소는 200자 이하여야 합니다.")
		String address,

		@Size(max = 200, message = "상세 주소는 200자 이하여야 합니다.")
		String addressDetail,

		Boolean isDefault) {
}
