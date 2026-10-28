package com.example.shopping.domain.notification.sender;

import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 알림톡 대행사가 아직 정해지지 않았을 때 쓴다. 보내지 않는다.
 * 대행사 구현을 추가하면 그 구현에 @Primary를 붙여 이 빈 대신 쓰게 한다.
 */
@Component
public class NotConfiguredAlimtalkSender implements AlimtalkSender {

	@Override
	public boolean isConfigured() {
		return false;
	}

	@Override
	public void send(String phoneNumber, String templateCode, Map<String, String> variables) {
		throw new IllegalStateException("Alimtalk provider is not configured");
	}
}
