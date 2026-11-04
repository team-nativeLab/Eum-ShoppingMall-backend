package com.example.shopping.domain.consent.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.consent.entity.MemberConsent;

public interface MemberConsentRepository extends JpaRepository<MemberConsent, Long> {

	List<MemberConsent> findByMemberIdOrderByIdAsc(Long memberId);
}
