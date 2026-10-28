package com.example.shopping.domain.notification.sender;

import java.util.Map;

/**
 * 카카오톡 채널 알림톡을 보낸다. 카카오는 직접 API를 열어 주지 않아 대행사(솔라피, NHN Cloud 등)를 거쳐 보낸다.
 * 대행사가 정해지면 이 인터페이스의 구현을 추가하고 AlimtalkConfiguration에서 고르게 한다.
 * 구현은 외부 호출 상한 시간(연결 2초, 응답 5초)을 둬야 한다.
 */
public interface AlimtalkSender {

	/** 대행사 설정이 있어 실제로 보낼 수 있는지. false면 알림을 SKIPPED로 남기고 보내지 않는다. */
	boolean isConfigured();

	/**
	 * @param phoneNumber  숫자만 남긴 휴대폰 번호
	 * @param templateCode 대행사 콘솔에서 승인받은 템플릿 코드
	 * @param variables    템플릿 변수 (예: #{문의제목} → "문의제목")
	 * @throws AlimtalkSendException 대행사가 거절했거나 응답하지 않은 경우
	 */
	void send(String phoneNumber, String templateCode, Map<String, String> variables);

	class AlimtalkSendException extends RuntimeException {

		public AlimtalkSendException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
