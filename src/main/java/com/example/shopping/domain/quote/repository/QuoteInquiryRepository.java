package com.example.shopping.domain.quote.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.quote.entity.QuoteInquiry;
import com.example.shopping.domain.quote.entity.QuoteStatus;

public interface QuoteInquiryRepository extends JpaRepository<QuoteInquiry, Long> {

	Page<QuoteInquiry> findByStatus(QuoteStatus status, Pageable pageable);

	Page<QuoteInquiry> findByMemberId(Long memberId, Pageable pageable);

	long countByStatus(QuoteStatus status);
}
