package com.example.shopping.domain.support.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.support.entity.Faq;

public interface FaqRepository extends JpaRepository<Faq, Long> {

	List<Faq> findAllByOrderBySortOrderAsc();
}
