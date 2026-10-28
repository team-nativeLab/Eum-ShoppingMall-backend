package com.example.shopping.domain.home.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.shopping.domain.home.entity.Banner;
import com.example.shopping.domain.upload.service.ImageReferences;

public interface BannerRepository extends JpaRepository<Banner, Long>, ImageReferences {

	List<Banner> findAllByOrderBySortOrderAscIdAsc();

	List<Banner> findByVisibleTrueOrderBySortOrderAscIdAsc();

	/** 참조 없는 이미지 정리용 */
	@Override
	@Query("select distinct b.imageUrl from Banner b")
	List<String> findAllImageUrls();
}
