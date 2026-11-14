package com.example.shopping.domain.event.dto.response;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.example.shopping.domain.event.entity.ShopEvent;
import com.example.shopping.domain.event.entity.ShopEventStatus;

/**
 * 고객 화면의 이벤트. 시각은 서버 시간대(Asia/Seoul) 오프셋을 붙인 ISO 8601이다(예: 2026-10-01T00:00:00+09:00).
 *
 * @param status 지금 시각 기준 진행 상태(ONGOING, UPCOMING, ENDED)
 */
public record ShopEventResponse(Long id, String title, String subtitle, ShopEventStatus status,
		OffsetDateTime startAt, OffsetDateTime endAt, String eyebrow, List<String> heroTitle, String heroDescription,
		String body, List<Section> sections, String searchKeyword) {

	public record Section(String heading, String body) {
	}

	public static ShopEventResponse of(ShopEvent event, LocalDateTime now) {
		return new ShopEventResponse(event.getId(), event.getTitle(), event.getSubtitle(), event.statusAt(now),
				withOffset(event.getStartAt()), withOffset(event.getEndAt()), event.getEyebrow(),
				event.getHeroTitleLines(), event.getHeroDescription(), event.getBody(), sectionsOf(event),
				event.getSearchKeyword());
	}

	/** 관리자 목록·편집용. 숨긴 이벤트도 포함한다. */
	public record Admin(Long id, String title, String subtitle, ShopEventStatus status, OffsetDateTime startAt,
			OffsetDateTime endAt, String eyebrow, List<String> heroTitle, String heroDescription, String body,
			List<Section> sections, String searchKeyword, boolean visible, int sortOrder) {

		public static Admin of(ShopEvent event, LocalDateTime now) {
			return new Admin(event.getId(), event.getTitle(), event.getSubtitle(), event.statusAt(now),
					withOffset(event.getStartAt()), withOffset(event.getEndAt()), event.getEyebrow(),
					event.getHeroTitleLines(), event.getHeroDescription(), event.getBody(), sectionsOf(event),
					event.getSearchKeyword(), event.isVisible(), event.getSortOrder());
		}
	}

	private static List<Section> sectionsOf(ShopEvent event) {
		return event.getSections().stream()
				.map(section -> new Section(section.getHeading(), section.getBody()))
				.toList();
	}

	/** 저장 시각은 서버 시간대(Asia/Seoul) 기준이다. */
	private static OffsetDateTime withOffset(LocalDateTime time) {
		return time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
	}
}
