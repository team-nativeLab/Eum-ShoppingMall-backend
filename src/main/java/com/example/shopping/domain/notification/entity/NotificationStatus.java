package com.example.shopping.domain.notification.entity;

public enum NotificationStatus {
	/** 보낼 차례를 기다리는 중 (실패 후 다시 보낼 때 포함) */
	PENDING,
	/** 한 서버가 보내는 중. 다른 서버가 같은 알림을 함께 보내지 않게 한다. */
	SENDING,
	SENT,
	/** 3번 모두 실패 */
	FAILED,
	/** 발송 수단(알림톡 대행사)이 설정되지 않아 보내지 않음 */
	SKIPPED
}
