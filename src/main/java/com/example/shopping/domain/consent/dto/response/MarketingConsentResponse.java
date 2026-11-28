package com.example.shopping.domain.consent.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.consent.entity.MemberConsent;

/**
 * 마케팅 정보 수신 동의 상태(#229)
 *
 * @param agreed    지금 동의한 상태인지
 * @param changedAt 마지막으로 동의하거나 철회한 시각. 처리 결과 안내(예: 2026년 11월 28일 수신 동의를 철회했습니다)에 쓴다.
 */
public record MarketingConsentResponse(boolean agreed, LocalDateTime changedAt) {

	public static MarketingConsentResponse from(MemberConsent consent) {
		return new MarketingConsentResponse(consent.isAgreed(), consent.getCreatedAt());
	}
}
