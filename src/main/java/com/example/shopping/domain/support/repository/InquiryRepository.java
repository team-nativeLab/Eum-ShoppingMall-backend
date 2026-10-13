package com.example.shopping.domain.support.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.support.entity.Inquiry;
import com.example.shopping.domain.support.entity.InquiryStatus;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

	Page<Inquiry> findByStatus(InquiryStatus status, Pageable pageable);

	Page<Inquiry> findByMemberId(Long memberId, Pageable pageable);

	long countByStatus(InquiryStatus status);
}
