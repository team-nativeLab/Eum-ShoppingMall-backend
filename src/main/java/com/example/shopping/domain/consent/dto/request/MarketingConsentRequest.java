package com.example.shopping.domain.consent.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 마케팅 정보 수신 동의 변경(#229)
 *
 * @param agreed true면 동의, false면 철회
 */
public record MarketingConsentRequest(
		@NotNull(message = "마케팅 정보 수신 동의 여부를 골라 주세요.")
		Boolean agreed) {
}
