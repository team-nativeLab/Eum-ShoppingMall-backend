package com.example.shopping.domain.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이벤트 상세 본문의 한 구성(소제목·본문) */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopEventSection {

	@Column(nullable = false, length = 100)
	private String heading;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String body;

	public ShopEventSection(String heading, String body) {
		this.heading = heading;
		this.body = body;
	}
}
