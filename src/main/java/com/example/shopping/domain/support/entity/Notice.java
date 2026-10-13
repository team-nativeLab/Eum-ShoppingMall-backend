package com.example.shopping.domain.support.entity;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	// 중요 공지 상단 고정
	@Column(nullable = false)
	private boolean pinned;

	// false면 비공개 공지
	@Column(nullable = false)
	private boolean visible = true;

	public static Notice create(String title, String content, boolean pinned, boolean visible) {
		Notice notice = new Notice();
		notice.title = title;
		notice.content = content;
		notice.pinned = pinned;
		notice.visible = visible;
		return notice;
	}

	/** null인 항목은 바꾸지 않는다. */
	public void update(String title, String content, Boolean pinned, Boolean visible) {
		if (title != null) {
			this.title = title;
		}
		if (content != null) {
			this.content = content;
		}
		if (pinned != null) {
			this.pinned = pinned;
		}
		if (visible != null) {
			this.visible = visible;
		}
	}
}
