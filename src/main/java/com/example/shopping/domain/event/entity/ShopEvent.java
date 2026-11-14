package com.example.shopping.domain.event.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이벤트·기획전. 진행 상태는 저장하지 않고 기간과 현재 시각으로 정한다(statusAt). */
@Entity
@Table(name = "shop_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopEvent extends BaseTimeEntity {

	/** 상세 화면 큰 제목의 줄을 이어 붙이는 구분자 */
	private static final String LINE_SEPARATOR = "\n";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, length = 200)
	private String subtitle;

	// 상세 제목 위 작은 문구
	@Column(nullable = false, length = 50)
	private String eyebrow;

	// 상세 화면 큰 제목. 줄을 줄바꿈으로 이어 저장한다(heroTitleLines).
	@Column(nullable = false, length = 200)
	private String heroTitle;

	@Column(nullable = false, length = 500)
	private String heroDescription;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String body;

	// 관련 상품을 찾을 검색어. 없으면 빈 문자열
	@Column(nullable = false, length = 50)
	private String searchKeyword;

	@Column(nullable = false)
	private LocalDateTime startAt;

	@Column(nullable = false)
	private LocalDateTime endAt;

	@Column(nullable = false)
	private boolean visible;

	// 작을수록 앞
	@Column(nullable = false)
	private int sortOrder;

	@ElementCollection
	@CollectionTable(name = "shop_event_section", joinColumns = @JoinColumn(name = "event_id"),
			foreignKey = @ForeignKey(name = "fk_shop_event_section_event"))
	@OrderColumn(name = "sort_order")
	private List<ShopEventSection> sections = new ArrayList<>();

	public static ShopEvent create(Fields fields) {
		ShopEvent event = new ShopEvent();
		event.change(fields);
		return event;
	}

	/** 전체 수정. 본문 구성도 통째로 바꾼다. */
	public void change(Fields fields) {
		this.title = fields.title();
		this.subtitle = fields.subtitle();
		this.eyebrow = fields.eyebrow();
		this.heroTitle = String.join(LINE_SEPARATOR, fields.heroTitle());
		this.heroDescription = fields.heroDescription();
		this.body = fields.body();
		this.searchKeyword = fields.searchKeyword();
		this.startAt = fields.startAt();
		this.endAt = fields.endAt();
		this.visible = fields.visible();
		this.sortOrder = fields.sortOrder();
		this.sections.clear();
		this.sections.addAll(fields.sections());
	}

	public List<String> getHeroTitleLines() {
		return List.of(heroTitle.split(LINE_SEPARATOR));
	}

	/** now 시각의 진행 상태. 종료 시각과 같은 시각까지는 진행 중이다. */
	public ShopEventStatus statusAt(LocalDateTime now) {
		if (now.isBefore(startAt)) {
			return ShopEventStatus.UPCOMING;
		}
		return now.isAfter(endAt) ? ShopEventStatus.ENDED : ShopEventStatus.ONGOING;
	}

	/** @param heroTitle 줄바꿈이 없는 제목 줄들 */
	public record Fields(String title, String subtitle, String eyebrow, List<String> heroTitle,
			String heroDescription, String body, List<ShopEventSection> sections, String searchKeyword,
			LocalDateTime startAt, LocalDateTime endAt, boolean visible, int sortOrder) {
	}
}
