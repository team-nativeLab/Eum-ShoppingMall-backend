package com.example.shopping.domain.event.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.event.dto.request.ShopEventRequest;
import com.example.shopping.domain.event.dto.response.ShopEventResponse;
import com.example.shopping.domain.event.entity.ShopEvent;
import com.example.shopping.domain.event.entity.ShopEventSection;
import com.example.shopping.domain.event.repository.ShopEventRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 관리자 이벤트 관리 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminShopEventService {

	private final ShopEventRepository shopEventRepository;

	/** 숨긴 이벤트를 포함한 전체. 정렬 순서, 같으면 최근 시작한 순 */
	public List<ShopEventResponse.Admin> list() {
		LocalDateTime now = LocalDateTime.now();
		return shopEventRepository.findAllByOrderBySortOrderAscStartAtDescIdDesc().stream()
				.map(event -> ShopEventResponse.Admin.of(event, now))
				.toList();
	}

	@Transactional
	public ShopEventResponse.Admin create(ShopEventRequest request) {
		ShopEvent event = shopEventRepository.save(ShopEvent.create(fields(request)));
		return ShopEventResponse.Admin.of(event, LocalDateTime.now());
	}

	/** 전체 수정. 본문 구성도 통째로 바꾼다. */
	@Transactional
	public ShopEventResponse.Admin update(Long eventId, ShopEventRequest request) {
		ShopEvent event = find(eventId);
		event.change(fields(request));
		return ShopEventResponse.Admin.of(event, LocalDateTime.now());
	}

	@Transactional
	public void delete(Long eventId) {
		shopEventRepository.delete(find(eventId));
	}

	private ShopEvent find(Long eventId) {
		return shopEventRepository.findById(eventId)
				.orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
	}

	private static ShopEvent.Fields fields(ShopEventRequest request) {
		LocalDateTime startAt = serverTime(request.startAt());
		LocalDateTime endAt = serverTime(request.endAt());
		if (endAt.isBefore(startAt)) {
			throw new BusinessException(ErrorCode.INVALID_EVENT_PERIOD);
		}
		List<ShopEventSection> sections = request.sections() == null ? List.of()
				: request.sections().stream()
						.map(section -> new ShopEventSection(section.heading().trim(), section.body().trim()))
						.toList();
		return new ShopEvent.Fields(request.title().trim(), text(request.subtitle()), text(request.eyebrow()),
				request.heroTitle().stream().map(String::trim).toList(), text(request.heroDescription()),
				text(request.body()), sections, text(request.searchKeyword()), startAt, endAt,
				request.visible() == null || request.visible(),
				request.sortOrder() == null ? 0 : request.sortOrder());
	}

	/** 요청의 시각을 서버 시간대(Asia/Seoul) 시각으로 바꿔 저장한다. */
	private static LocalDateTime serverTime(OffsetDateTime time) {
		return time.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
	}

	private static String text(String value) {
		return StringUtils.hasText(value) ? value.trim() : "";
	}
}
