package com.example.shopping.domain.home.dto;

import org.springframework.util.StringUtils;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 푸터에 보이는 사업자·고객센터 정보. 관리자가 통째로 저장하고(PUT /api/v1/admin/company) 이 모양 그대로 JSON으로 저장한다.
 * 전자상거래법(제10조)은 상호·대표자·주소·전화번호·이메일·사업자등록번호·통신판매업 신고번호·개인정보 보호책임자를
 * 사이버몰 첫 화면에 표시하도록 한다. 신고 전에도 나머지를 저장할 수 있게 통신판매업 신고번호와 개인정보 보호책임자는 비워 둘 수 있다.
 *
 * @param mailOrderSalesNumber 통신판매업 신고번호. 없으면 빈 문자열
 * @param quoteEmail           견적 문의 담당 이메일. 없으면 null(견적 화면에서 연락처를 생략)
 * @param privacyOfficer       개인정보 보호책임자. 없으면 빈 문자열
 * @param customerCenterPhone  화면에 그대로 보여 줄 고객센터 번호(내선 안내 등 포함 가능)
 * @param customerCenterTel    전화 걸기(tel:) 링크용 번호. 숫자와 맨 앞 +만 쓴다. 없으면 null(화면이 표시 번호에서 뽑는다)
 * @param customerCenterHours  고객센터 운영 시간 안내
 */
public record CompanyInfo(
		@NotBlank(message = "상호를 입력해 주세요.")
		@Size(max = 100, message = "상호는 100자 이하여야 합니다.")
		String companyName,

		@NotBlank(message = "대표자를 입력해 주세요.")
		@Size(max = 50, message = "대표자는 50자 이하여야 합니다.")
		String representative,

		@NotBlank(message = "사업자등록번호를 입력해 주세요.")
		@Pattern(regexp = "^[0-9]{3}-[0-9]{2}-[0-9]{5}$", message = "사업자등록번호는 000-00-00000 형식입니다.")
		String businessRegistrationNumber,

		@Size(max = 50, message = "통신판매업 신고번호는 50자 이하여야 합니다.")
		String mailOrderSalesNumber,

		@NotBlank(message = "사업장 주소를 입력해 주세요.")
		@Size(max = 200, message = "사업장 주소는 200자 이하여야 합니다.")
		String address,

		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Pattern(regexp = ValidationPatterns.ASCII_EMAIL, message = "이메일은 영문·숫자·기호로만 입력해 주세요.")
		@Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
		String email,

		@Email(message = "견적 문의 이메일 형식이 올바르지 않습니다.")
		@Pattern(regexp = ValidationPatterns.ASCII_EMAIL, message = "이메일은 영문·숫자·기호로만 입력해 주세요.")
		@Size(max = 100, message = "견적 문의 이메일은 100자 이하여야 합니다.")
		String quoteEmail,

		@Size(max = 50, message = "개인정보 보호책임자는 50자 이하여야 합니다.")
		String privacyOfficer,

		@NotBlank(message = "고객센터 번호를 입력해 주세요.")
		@Size(max = 50, message = "고객센터 번호는 50자 이하여야 합니다.")
		String customerCenterPhone,

		@Pattern(regexp = "^\\+?[0-9]{2,20}$", message = "전화 걸기 번호는 숫자(맨 앞 + 허용)만 입력해 주세요.")
		String customerCenterTel,

		@NotBlank(message = "고객센터 운영 시간을 입력해 주세요.")
		@Size(max = 100, message = "고객센터 운영 시간은 100자 이하여야 합니다.")
		String customerCenterHours) {

	/** 앞뒤 공백을 지우고, 비워 둘 수 있는 값은 빈 문자열이나 null로 맞춘다. */
	public CompanyInfo {
		companyName = trim(companyName);
		representative = trim(representative);
		businessRegistrationNumber = trim(businessRegistrationNumber);
		mailOrderSalesNumber = StringUtils.hasText(mailOrderSalesNumber) ? mailOrderSalesNumber.trim() : "";
		address = trim(address);
		email = trim(email);
		quoteEmail = StringUtils.hasText(quoteEmail) ? quoteEmail.trim() : null;
		privacyOfficer = StringUtils.hasText(privacyOfficer) ? privacyOfficer.trim() : "";
		customerCenterPhone = trim(customerCenterPhone);
		customerCenterTel = StringUtils.hasText(customerCenterTel) ? customerCenterTel.trim() : null;
		customerCenterHours = trim(customerCenterHours);
	}

	private static String trim(String value) {
		return value == null ? null : value.trim();
	}
}
