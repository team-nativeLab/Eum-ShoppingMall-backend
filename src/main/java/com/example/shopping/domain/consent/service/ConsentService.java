package com.example.shopping.domain.consent.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.consent.entity.ConsentType;
import com.example.shopping.domain.consent.entity.MemberConsent;
import com.example.shopping.domain.consent.repository.MemberConsentRepository;
import com.example.shopping.domain.home.entity.SiteContent;
import com.example.shopping.domain.home.repository.SiteContentRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 약관·개인정보 수집·마케팅 수신 동의를 기록한다(#21).
 * 동의한 문서의 버전은 그 시점 고정 콘텐츠(terms, privacy-policy)의 수정 시각으로 남긴다.
 * 관리자가 본문을 고치면 수정 시각이 바뀌므로 누가 어떤 본문에 동의했는지 가릴 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ConsentService {

	private final MemberConsentRepository memberConsentRepository;
	private final SiteContentRepository siteContentRepository;

	/** 가입한 회원의 동의를 남긴다. 필수 항목 동의 여부는 요청 검증에서 이미 확인했다. */
	public void recordSignup(Long memberId, boolean marketingAgreed) {
		memberConsentRepository.saveAll(List.of(
				MemberConsent.of(memberId, ConsentType.TERMS, true, version(SiteContent.TERMS)),
				MemberConsent.of(memberId, ConsentType.PRIVACY, true, version(SiteContent.PRIVACY_POLICY)),
				MemberConsent.of(memberId, ConsentType.MARKETING, marketingAgreed, null)));
	}

	/**
	 * 1:1 문의·견적 접수 때의 개인정보 수집 동의. 비회원은 동의하지 않으면 400이다.
	 * 회원은 가입할 때 이미 동의했으므로 요청에 없어도 된다.
	 *
	 * @return 동의했으면 동의 시각과 개인정보 처리방침 버전, 아니면 null
	 */
	@Transactional(readOnly = true)
	public PrivacyConsent privacyConsentForSubmission(Long memberId, Boolean privacyAgreed) {
		if (!Boolean.TRUE.equals(privacyAgreed)) {
			if (memberId == null) {
				throw new BusinessException(ErrorCode.PRIVACY_CONSENT_REQUIRED);
			}
			return null;
		}
		return new PrivacyConsent(LocalDateTime.now(), version(SiteContent.PRIVACY_POLICY));
	}

	private String version(String code) {
		return siteContentRepository.findByCode(code)
				.map(content -> content.getUpdatedAt().toString())
				.orElse(null);
	}

	/** @param policyVersion 개인정보 처리방침이 아직 등록되지 않았으면 null */
	public record PrivacyConsent(LocalDateTime agreedAt, String policyVersion) {
	}
}
