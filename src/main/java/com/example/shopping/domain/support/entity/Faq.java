package com.example.shopping.domain.support.entity;

import org.hibernate.annotations.ColumnDefault;

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
public class Faq {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String category;

	@Column(nullable = false, length = 300)
	private String question;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String answer;

	@Column(nullable = false)
	private int sortOrder;

	// false면 고객 FAQ 목록에 나오지 않는다(#220).
	@ColumnDefault("true")
	@Column(nullable = false)
	private boolean visible = true;

	public static Faq create(String category, String question, String answer, int sortOrder, boolean visible) {
		Faq faq = new Faq();
		faq.category = category;
		faq.question = question;
		faq.answer = answer;
		faq.sortOrder = sortOrder;
		faq.visible = visible;
		return faq;
	}

	/** null인 항목은 바꾸지 않는다. */
	public void update(String category, String question, String answer, Integer sortOrder, Boolean visible) {
		if (category != null) {
			this.category = category;
		}
		if (question != null) {
			this.question = question;
		}
		if (answer != null) {
			this.answer = answer;
		}
		if (sortOrder != null) {
			this.sortOrder = sortOrder;
		}
		if (visible != null) {
			this.visible = visible;
		}
	}
}
