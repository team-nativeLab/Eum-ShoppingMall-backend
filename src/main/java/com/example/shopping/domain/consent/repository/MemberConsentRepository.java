package com.example.shopping.domain.consent.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.consent.entity.ConsentType;
import com.example.shopping.domain.consent.entity.MemberConsent;

public interface MemberConsentRepository extends JpaRepository<MemberConsent, Long> {

	List<MemberConsent> findByMemberIdOrderByIdAsc(Long memberId);

	/** 항목의 가장 최근 기록. 동의를 바꿀 때마다 새 행을 남기므로 이것이 지금 상태다. */
	Optional<MemberConsent> findFirstByMemberIdAndConsentTypeOrderByIdDesc(Long memberId, ConsentType consentType);
}
