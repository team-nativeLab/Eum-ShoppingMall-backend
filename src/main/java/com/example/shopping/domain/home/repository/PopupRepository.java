package com.example.shopping.domain.home.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.shopping.domain.home.entity.Popup;
import com.example.shopping.domain.upload.service.ImageReferences;

public interface PopupRepository extends JpaRepository<Popup, Long>, ImageReferences {

	/** 정렬 순서가 같으면 최근 등록한 것이 앞 */
	List<Popup> findAllByOrderBySortOrderAscIdDesc();

	List<Popup> findByVisibleTrueOrderBySortOrderAscIdDesc();

	/** 참조 없는 이미지 정리가 팝업 사진을 지우지 않도록 알려 준다. */
	@Override
	@Query("select distinct p.imageUrl from Popup p where p.imageUrl is not null")
	List<String> findAllImageUrls();
}
