package com.example.shopping.domain.product.dto.request;

import com.example.shopping.domain.product.entity.SupplierOrderChannel;
import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 발주처 등록·수정(전체 수정, #208).
 *
 * @param companyName  회사명(100자 이하)
 * @param contactName  담당자 이름(50자 이하)
 * @param contactPhone 담당자 연락처(예: 02-1234-5678, 010-1234-5678)
 * @param contactEmail 담당자 이메일(100자 이하)
 * @param orderChannel 주문(발주) 정보를 받는 방식. EMAIL이면 orderEmail, FAX면 orderFax가 필요하다.
 * @param orderEmail   주문을 받을 이메일. 수신 방식이 EMAIL이 아니면 저장하지 않는다.
 * @param orderFax     주문을 받을 팩스 번호. 수신 방식이 FAX가 아니면 저장하지 않는다.
 * @param address      주소. 없으면 빈 문자열
 * @param memo         관리자 메모. 없으면 빈 문자열
 */
public record SupplierRequest(
		@NotBlank(message = "회사명을 입력해 주세요.")
		@Size(max = 100, message = "회사명은 100자 이하여야 합니다.")
		String companyName,

		@NotBlank(message = "담당자 이름을 입력해 주세요.")
		@Size(max = 50, message = "담당자 이름은 50자 이하여야 합니다.")
		String contactName,

		@NotBlank(message = "담당자 연락처를 입력해 주세요.")
		@Pattern(regexp = ValidationPatterns.PHONE, message = "연락처 형식이 올바르지 않습니다.")
		String contactPhone,

		@NotBlank(message = "담당자 이메일을 입력해 주세요.")
		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Pattern(regexp = ValidationPatterns.ASCII_EMAIL, message = "이메일은 영문·숫자·기호로만 입력해 주세요.")
		@Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
		String contactEmail,

		@NotNull(message = "주문 수신 방식을 골라 주세요.")
		SupplierOrderChannel orderChannel,

		@Email(message = "주문 수신 이메일 형식이 올바르지 않습니다.")
		@Pattern(regexp = "^$|" + ValidationPatterns.ASCII_EMAIL, message = "이메일은 영문·숫자·기호로만 입력해 주세요.")
		@Size(max = 100, message = "주문 수신 이메일은 100자 이하여야 합니다.")
		String orderEmail,

		@Pattern(regexp = ValidationPatterns.PHONE_OR_EMPTY, message = "팩스 번호 형식이 올바르지 않습니다.")
		String orderFax,

		@Size(max = 200, message = "주소는 200자 이하여야 합니다.")
		String address,

		@Size(max = 1000, message = "메모는 1,000자 이하여야 합니다.")
		String memo) {
}
