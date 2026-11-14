package com.example.shopping.domain.event.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.event.entity.ShopEvent;

public interface ShopEventRepository extends JpaRepository<ShopEvent, Long> {

	/** 고객 화면의 이벤트 목록. 정렬 순서, 같으면 최근 시작한 순 */
	@EntityGraph(attributePaths = "sections")
	List<ShopEvent> findByVisibleTrueOrderBySortOrderAscStartAtDescIdDesc();

	/** 관리자 목록. 숨긴 이벤트도 포함한다. */
	@EntityGraph(attributePaths = "sections")
	List<ShopEvent> findAllByOrderBySortOrderAscStartAtDescIdDesc();

	Optional<ShopEvent> findByIdAndVisibleTrue(Long id);
}
