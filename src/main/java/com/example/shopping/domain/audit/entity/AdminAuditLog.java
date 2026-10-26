package com.example.shopping.domain.audit.entity;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 관리자 로그인 실패처럼 누가 했는지 모르는 기록은 비어 있다. */
	@Column
	private Long actorMemberId;

	@Column(nullable = false, length = 45)
	private String ipAddress;

	@Column(nullable = false, length = 10)
	private String action;

	@Column(nullable = false, length = 50)
	private String targetType;

	@Column(length = 100)
	private String targetId;

	@Column(nullable = false, length = 500)
	private String summary;

	/** 바꾼 항목과 값(요청 본문). 비밀번호 같은 민감한 값은 가려서 남긴다. */
	@Column(columnDefinition = "TEXT")
	private String details;

	@Column(nullable = false)
	private int responseStatus;

	public static AdminAuditLog create(Long actorMemberId, String ipAddress, String action, String targetType,
			String targetId, String summary, String details, int responseStatus) {
		AdminAuditLog log = new AdminAuditLog();
		log.actorMemberId = actorMemberId;
		log.ipAddress = ipAddress;
		log.action = action;
		log.targetType = targetType;
		log.targetId = targetId;
		log.summary = summary;
		log.details = details;
		log.responseStatus = responseStatus;
		return log;
	}
}
