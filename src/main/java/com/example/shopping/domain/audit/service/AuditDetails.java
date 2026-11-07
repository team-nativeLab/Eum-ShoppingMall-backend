package com.example.shopping.domain.audit.service;

import java.util.ArrayList;
import java.util.regex.Pattern;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 감사 로그에 남길 변경 내용을 만든다. 민감한 값은 가리고 길이를 제한한다. */
public final class AuditDetails {

	static final int MAX_LENGTH = 2000;
	static final String MASK = "***";

	/**
	 * 이름에 이 단어가 들어간 항목은 값을 남기지 않는다. 감사 로그는 2년 동안 남으므로 원래 데이터를 지운 뒤에도 남으면 안 되는 값이다(#139).
	 * <ul>
	 * <li>비밀 값: password, currentPassword, secretKey, refreshToken, cardNumber 등</li>
	 * <li>연락처: email, phone, address, zipCode, recipient 등</li>
	 * <li>자유롭게 쓰는 글: 회원 정지·주문 취소·재고 조정 사유(reason), 문의·FAQ 답변(answer), 메모(memo, note, comment).
	 * 고객 이름이나 연락처가 섞일 수 있다.</li>
	 * </ul>
	 */
	private static final Pattern SENSITIVE_NAME = Pattern.compile(
			"(?i).*(password|secret|token|card|email|phone|mobile|address|zipcode|recipient|reason|answer|memo|note|comment).*");
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private AuditDetails() {
	}

	/** JSON 요청 본문에서 민감한 값을 가린다. JSON이 아니거나 비어 있으면 null */
	public static String fromJsonBody(byte[] body) {
		if (body == null || body.length == 0) {
			return null;
		}
		JsonNode node;
		try {
			node = JSON.readTree(body);
		} catch (JacksonException e) {
			return null;
		}
		if (node == null || node.isMissingNode()) {
			return null;
		}
		mask(node);
		return truncate(JSON.writeValueAsString(node));
	}

	static String ofEmail(String email) {
		ObjectNode node = JSON.createObjectNode();
		node.put("email", email);
		return truncate(JSON.writeValueAsString(node));
	}

	private static void mask(JsonNode node) {
		if (node instanceof ObjectNode object) {
			for (String name : new ArrayList<>(object.propertyNames())) {
				if (SENSITIVE_NAME.matcher(name).matches()) {
					object.put(name, MASK);
				} else {
					mask(object.get(name));
				}
			}
		} else if (node.isArray()) {
			for (JsonNode child : node) {
				mask(child);
			}
		}
	}

	private static String truncate(String value) {
		return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH) + "...(생략)";
	}
}
