package com.example.shopping.domain.home.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.home.entity.SiteContent;

public interface SiteContentRepository extends JpaRepository<SiteContent, Long> {

	List<SiteContent> findAllByOrderByCodeAsc();

	Optional<SiteContent> findByCode(String code);
}
