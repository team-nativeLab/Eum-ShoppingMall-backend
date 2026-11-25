package com.example.shopping.domain.support.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.support.entity.Faq;

public interface FaqRepository extends JpaRepository<Faq, Long> {

	/** 고객 FAQ 목록: 노출하는 FAQ만 정렬 순서대로 */
	List<Faq> findByVisibleTrueOrderBySortOrderAscIdAsc();

	/** 관리자 FAQ 목록: 숨긴 FAQ까지 정렬 순서대로 */
	List<Faq> findAllByOrderBySortOrderAscIdAsc();
}
