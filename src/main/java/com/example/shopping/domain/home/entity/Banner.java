package com.example.shopping.domain.home.entity;

import org.springframework.util.StringUtils;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 홈 배너 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Banner extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 파일 업로드 API가 돌려준 이미지 URL
	@Column(nullable = false, length = 500)
	private String imageUrl;

	// 클릭 시 이동할 대상
	@Column(length = 500)
	private String linkUrl;

	@Column(nullable = false)
	private int sortOrder;

	@Column(nullable = false)
	private boolean visible = true;

	public static Banner create(String imageUrl, String linkUrl, int sortOrder, boolean visible) {
		Banner banner = new Banner();
		banner.imageUrl = imageUrl;
		banner.linkUrl = blankToNull(linkUrl);
		banner.sortOrder = sortOrder;
		banner.visible = visible;
		return banner;
	}

	/** null인 항목은 바꾸지 않는다. 연결 주소는 빈 문자열을 주면 지운다. */
	public void update(String imageUrl, String linkUrl, Integer sortOrder, Boolean visible) {
		if (imageUrl != null) {
			this.imageUrl = imageUrl;
		}
		if (linkUrl != null) {
			this.linkUrl = blankToNull(linkUrl);
		}
		if (sortOrder != null) {
			this.sortOrder = sortOrder;
		}
		if (visible != null) {
			this.visible = visible;
		}
	}

	private static String blankToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
}
