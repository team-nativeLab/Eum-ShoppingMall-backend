package com.example.shopping.domain.product.entity;

/** 발주처가 주문(발주) 정보를 받는 방식 */
public enum SupplierOrderChannel {
	/** 이메일(orderEmail로 보낸다) */
	EMAIL,
	/** 팩스(orderFax로 보낸다) */
	FAX
}
