package com.example.shopping.domain.event.dto.response;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.example.shopping.domain.event.entity.ShopEvent;
import com.example.shopping.domain.event.entity.ShopEventStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 고객 화면의 이벤트. 시각은 서버 시간대(Asia/Seoul) 오프셋을 붙인 ISO 8601이다(예: 2026-10-01T00:00:00+09:00).
 *
 * @param id              이벤트 ID
 * @param title           이벤트명
 * @param subtitle        목록 카드의 부제. 없으면 빈 문자열
 * @param status          지금 시각 기준 진행 상태(ONGOING, UPCOMING, ENDED)
 * @param startAt         시작 시각
 * @param endAt           종료 시각. 이 시각까지 진행 중이다.
 * @param eyebrow         상세 제목 위 작은 문구. 없으면 빈 문자열
 * @param heroTitle       상세 화면 큰 제목 줄(1~3줄)
 * @param heroDescription 큰 제목 아래 설명. 없으면 빈 문자열
 * @param body            상세 본문. 없으면 빈 문자열
 * @param sections        상세 본문 구성(소제목·본문). 없으면 빈 배열
 * @param searchKeyword   관련 상품을 찾을 검색어. 없으면 빈 문자열
 */
public record ShopEventResponse(Long id, String title, String subtitle, ShopEventStatus status,
		OffsetDateTime startAt, OffsetDateTime endAt, String eyebrow, List<String> heroTitle, String heroDescription,
		String body, List<Section> sections, String searchKeyword) {

	/**
	 * 상세 본문의 한 구성
	 *
	 * @param heading 소제목
	 * @param body    본문
	 */
	@Schema(name = "ShopEventSectionResponse")
	public record Section(String heading, String body) {
	}

	public static ShopEventResponse of(ShopEvent event, LocalDateTime now) {
		return new ShopEventResponse(event.getId(), event.getTitle(), event.getSubtitle(), event.statusAt(now),
				withOffset(event.getStartAt()), withOffset(event.getEndAt()), event.getEyebrow(),
				event.getHeroTitleLines(), event.getHeroDescription(), event.getBody(), sectionsOf(event),
				event.getSearchKeyword());
	}

	/**
	 * 관리자 목록·편집용. 숨긴 이벤트도 포함한다.
	 *
	 * @param id              이벤트 ID
	 * @param title           이벤트명
	 * @param subtitle        목록 카드의 부제
	 * @param status          지금 시각 기준 진행 상태(ONGOING, UPCOMING, ENDED)
	 * @param startAt         시작 시각
	 * @param endAt           종료 시각
	 * @param eyebrow         상세 제목 위 작은 문구
	 * @param heroTitle       상세 화면 큰 제목 줄(1~3줄)
	 * @param heroDescription 큰 제목 아래 설명
	 * @param body            상세 본문
	 * @param sections        상세 본문 구성(소제목·본문)
	 * @param searchKeyword   관련 상품을 찾을 검색어
	 * @param visible         고객 화면에 보이는지
	 * @param sortOrder       정렬 순서. 작을수록 앞
	 */
	@Schema(name = "ShopEventAdminResponse")
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
