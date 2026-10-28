package com.example.shopping.domain.notification.entity;

import java.time.Duration;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고객에게 보낼(보낸) 알림 한 건. 보낼 일이 생긴 트랜잭션에서 함께 저장하고, 커밋된 뒤 따로 보낸다.
 * 실패하면 1분, 5분 뒤 다시 보내고, 3번 실패하면 FAILED로 남긴다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification_message", indexes = {
		@Index(name = "ix_notification_status_next", columnList = "status, next_attempt_at"),
		@Index(name = "ix_notification_reference", columnList = "reference_type, reference_id") })
public class NotificationMessage extends BaseTimeEntity {

	public static final int MAX_ATTEMPTS = 3;
	private static final Duration[] RETRY_DELAYS = { Duration.ofMinutes(1), Duration.ofMinutes(5) };

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private NotificationChannel channel;

	/** 대행사 콘솔에서 승인받은 알림톡 템플릿 코드 */
	@Column(nullable = false, length = 100)
	private String templateCode;

	/** 받는 사람 (알림톡은 숫자만 남긴 휴대폰 번호) */
	@Column(nullable = false, length = 255)
	private String recipient;

	/** 템플릿 변수 (JSON 객체) */
	@Column(nullable = false, columnDefinition = "TEXT")
	private String variables;

	/** 무엇 때문에 보낸 알림인지 (예: INQUIRY_ANSWER) */
	@Column(name = "reference_type", nullable = false, length = 50)
	private String referenceType;

	@Column(name = "reference_id", nullable = false)
	private Long referenceId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private NotificationStatus status = NotificationStatus.PENDING;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "next_attempt_at", nullable = false)
	private LocalDateTime nextAttemptAt;

	@Column(length = 500)
	private String lastError;

	private LocalDateTime sentAt;

	public static NotificationMessage create(NotificationChannel channel, String templateCode, String recipient,
			String variables, String referenceType, Long referenceId) {
		NotificationMessage message = new NotificationMessage();
		message.channel = channel;
		message.templateCode = templateCode;
		message.recipient = recipient;
		message.variables = variables;
		message.referenceType = referenceType;
		message.referenceId = referenceId;
		message.nextAttemptAt = LocalDateTime.now();
		return message;
	}

	public void markSent(LocalDateTime now) {
		this.attempts++;
		this.status = NotificationStatus.SENT;
		this.sentAt = now;
		this.lastError = null;
	}

	/** 발송 수단이 설정되지 않아 보내지 않았다. 다시 보내지 않는다. */
	public void markSkipped(String reason) {
		this.status = NotificationStatus.SKIPPED;
		this.lastError = truncate(reason);
	}

	/** 실패를 기록한다. 아직 횟수가 남았으면 다음 시도 시각을 정하고, 다 썼으면 FAILED로 끝낸다. */
	public void markFailed(String error, LocalDateTime now) {
		this.attempts++;
		this.lastError = truncate(error);
		if (attempts >= MAX_ATTEMPTS) {
			this.status = NotificationStatus.FAILED;
			return;
		}
		this.status = NotificationStatus.PENDING;
		this.nextAttemptAt = now.plus(RETRY_DELAYS[Math.min(attempts - 1, RETRY_DELAYS.length - 1)]);
	}

	private static String truncate(String value) {
		if (value == null) {
			return null;
		}
		return value.length() <= 500 ? value : value.substring(0, 500);
	}
}
