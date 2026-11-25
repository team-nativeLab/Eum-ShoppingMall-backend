package com.example.shopping.domain.home.entity;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 웹 홈 첫 화면에 띄우는 팝업(#211). 관리자가 입력한 글은 화면에서 텍스트로만 그린다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Popup extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String title;

	// 업로드 API가 돌려준 주소나 웹의 정적 이미지(/assets/...). 사진이 없으면 null
	@Column(length = 500)
	private String imageUrl;

	@Column(nullable = false, length = 2000)
	private String body;

	@Column(nullable = false)
	private boolean visible;

	@Column(nullable = false)
	private int sortOrder;

	public static Popup create(Fields fields) {
		Popup popup = new Popup();
		popup.change(fields);
		return popup;
	}

	public void change(Fields fields) {
		this.title = fields.title();
		this.imageUrl = fields.imageUrl();
		this.body = fields.body();
		this.visible = fields.visible();
		this.sortOrder = fields.sortOrder();
	}

	/** 저장할 값. 사진이 없으면 imageUrl은 null이다. */
	public record Fields(String title, String imageUrl, String body, boolean visible, int sortOrder) {
	}
}
