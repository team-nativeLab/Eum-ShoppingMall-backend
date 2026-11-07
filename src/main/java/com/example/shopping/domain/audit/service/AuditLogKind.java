package com.example.shopping.domain.audit.service;

/** 감사 로그 조회에서 기록 종류를 거를 때 쓴다(#138). 기록의 action(HTTP 메서드 또는 LOGIN)으로 나눈다. */
public enum AuditLogKind {
	/** 개인정보 조회(GET) */
	READ,
	/** 변경(POST·PUT·PATCH·DELETE) */
	CHANGE,
	/** 관리자 로그인 성공·실패 */
	LOGIN
}
