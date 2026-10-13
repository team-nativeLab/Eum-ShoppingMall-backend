package com.example.shopping.domain.home.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.home.entity.Banner;

public interface BannerRepository extends JpaRepository<Banner, Long> {

	List<Banner> findAllByOrderBySortOrderAscIdAsc();

	List<Banner> findByVisibleTrueOrderBySortOrderAscIdAsc();
}
