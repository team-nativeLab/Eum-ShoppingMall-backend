package com.example.shopping.domain.legal.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 약관·정책 문서 한 버전의 저장. 같은 버전이 있으면 고치고, 없으면 새 버전으로 만든다.
 * 시행 중이거나 지난 공개 버전은 고칠 수 없다(409 LEGAL_DOCUMENT_LOCKED).
 *
 * @param version     버전 이름(예: 2.0). 영문·숫자로 시작하고 영문·숫자·.·-·_ 30자 이내
 * @param effectiveAt 시행일(예: 2026-11-01). 이날 0시부터 시행한다.
 * @param body        평문 본문. HTML 태그는 받지 않는다.
 * @param published   공개 여부. 공개하고 시행일이 되면 고객 화면의 현재 문서가 된다.
 */
public record LegalDocumentRequest(
		@NotBlank(message = "버전을 입력해 주세요.")
		@Pattern(regexp = "^[0-9A-Za-z][0-9A-Za-z._-]{0,29}$", message = "버전은 영문·숫자로 시작하고 영문·숫자·.·-·_ 30자 이내입니다.")
		String version,

		@NotBlank(message = "문서명을 입력해 주세요.")
		@Size(max = 200, message = "문서명은 200자 이하여야 합니다.")
		String title,

		@NotBlank(message = "본문을 입력해 주세요.")
		@Size(max = 100_000, message = "본문은 100,000자 이하여야 합니다.")
		String body,

		@NotNull(message = "시행일을 입력해 주세요.")
		LocalDate effectiveAt,

		@NotNull(message = "공개 여부를 골라 주세요.")
		Boolean published) {
}
