package com.example.shopping.domain.history.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.history.entity.StatusHistory;
import com.example.shopping.domain.history.entity.StatusHistoryTarget;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

	/** 기록한 순서대로 */
	List<StatusHistory> findByTargetTypeAndTargetIdOrderByIdAsc(StatusHistoryTarget targetType, Long targetId);
}
