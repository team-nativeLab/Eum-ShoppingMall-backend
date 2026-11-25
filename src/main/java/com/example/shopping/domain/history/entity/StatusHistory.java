package com.example.shopping.domain.history.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문·문의의 상태가 바뀐 기록(#223). 관리자 상세 화면의 처리 이력으로 보여 준다. 고치거나 지우지 않고 쌓기만 한다.
 * 메모(note)는 정해 둔 문구만 넣어 개인정보가 들어가지 않게 한다.
 */
@Entity
@Table(name = "status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StatusHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private StatusHistoryTarget targetType;

	@Column(nullable = false)
	private Long targetId;

	// 바뀐 뒤 상태(각 대상의 상태 enum 이름)
	@Column(nullable = false, length = 30)
	private String status;

	@Column(nullable = false, length = 100)
	private String note;

	@Column(nullable = false)
	private LocalDateTime occurredAt;

	public static StatusHistory of(StatusHistoryTarget targetType, Long targetId, String status, String note,
			LocalDateTime occurredAt) {
		StatusHistory history = new StatusHistory();
		history.targetType = targetType;
		history.targetId = targetId;
		history.status = status;
		history.note = note;
		history.occurredAt = occurredAt;
		return history;
	}
}
