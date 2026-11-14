package com.example.shopping.domain.event.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.event.dto.response.ShopEventResponse;
import com.example.shopping.domain.event.repository.ShopEventRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

/** 고객 화면의 이벤트 목록·상세. 숨긴 이벤트는 보이지 않는다. 진행 상태는 조회 시각으로 정한다. */
@Service
@Transactional(readOnly = true)
public class ShopEventService {

	private final ShopEventRepository shopEventRepository;
	private final Clock clock;

	@Autowired
	public ShopEventService(ShopEventRepository shopEventRepository) {
		this(shopEventRepository, Clock.systemDefaultZone());
	}

	ShopEventService(ShopEventRepository shopEventRepository, Clock clock) {
		this.shopEventRepository = shopEventRepository;
		this.clock = clock;
	}

	/** 보이는 이벤트. 정렬 순서, 같으면 최근 시작한 순. 끝난 이벤트도 상태(ENDED)를 달아 준다. */
	public List<ShopEventResponse> list() {
		LocalDateTime now = LocalDateTime.now(clock);
		return shopEventRepository.findByVisibleTrueOrderBySortOrderAscStartAtDescIdDesc().stream()
				.map(event -> ShopEventResponse.of(event, now))
				.toList();
	}

	public ShopEventResponse get(Long eventId) {
		return shopEventRepository.findByIdAndVisibleTrue(eventId)
				.map(event -> ShopEventResponse.of(event, LocalDateTime.now(clock)))
				.orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
	}
}
