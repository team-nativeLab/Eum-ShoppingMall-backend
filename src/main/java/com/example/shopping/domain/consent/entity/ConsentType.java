package com.example.shopping.domain.consent.entity;

/** 회원가입 때 받는 동의 항목 */
public enum ConsentType {
	/** 이용약관 (필수) */
	TERMS,
	/** 개인정보 수집·이용 (필수) */
	PRIVACY,
	/** 마케팅 정보 수신 (선택) */
	MARKETING
}
