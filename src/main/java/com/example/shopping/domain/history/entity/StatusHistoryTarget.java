package com.example.shopping.domain.history.entity;

/** 상태 변경 이력을 남기는 대상(#223) */
public enum StatusHistoryTarget {
	/** 주문(OrderStatus) */
	ORDER,
	/** 1:1 문의(InquiryStatus) */
	INQUIRY,
	/** 상품 문의(ProductQuestionStatus) */
	PRODUCT_QUESTION
}
