package com.example.shopping.domain.product.entity;

import java.util.Set;

public enum ProductStatus {
	ON_SALE, SOLD_OUT, HIDDEN, DELETED;

	/** 고객에게 노출되는 상태. HIDDEN(비공개)·DELETED(소프트 삭제)는 관리자만 본다. */
	public static final Set<ProductStatus> CUSTOMER_VISIBLE = Set.of(ON_SALE, SOLD_OUT);

	/** 관리자가 다루는 상태. 삭제된 상품은 관리자 화면에서도 빠진다. */
	public static final Set<ProductStatus> MANAGEABLE = Set.of(ON_SALE, SOLD_OUT, HIDDEN);
}
