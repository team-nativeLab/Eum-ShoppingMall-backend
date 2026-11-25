package com.example.shopping.domain.history.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.history.dto.StatusHistoryResponse;
import com.example.shopping.domain.history.entity.StatusHistory;
import com.example.shopping.domain.history.entity.StatusHistoryTarget;
import com.example.shopping.domain.history.repository.StatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/** 상태 변경 이력(#223). 상태를 바꾸는 트랜잭션 안에서 남겨, 상태 변경이 취소되면 이력도 남지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatusHistoryService {

	private final StatusHistoryRepository statusHistoryRepository;

	@Transactional
	public void record(StatusHistoryTarget targetType, Long targetId, Enum<?> status, String note) {
		statusHistoryRepository.save(StatusHistory.of(targetType, targetId, status.name(), note, LocalDateTime.now()));
	}

	/** 기록한 순서대로. 없으면 빈 목록 */
	public List<StatusHistoryResponse> list(StatusHistoryTarget targetType, Long targetId) {
		return statusHistoryRepository.findByTargetTypeAndTargetIdOrderByIdAsc(targetType, targetId).stream()
				.map(StatusHistoryResponse::from)
				.toList();
	}
}
