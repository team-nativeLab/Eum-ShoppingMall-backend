package com.example.shopping.domain.order;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;


/** Server-side pricing policy shared by cart estimates and persisted orders. */
public record OrderAmounts(long itemAmount, long discountAmount, long shippingFee, long totalAmount) {

	private static final long FREE_SHIPPING_THRESHOLD = 100_000;
	private static final long SHIPPING_FEE = 5_000;

	public static OrderAmounts calculate(long itemAmount) {
		if (itemAmount < 0) {
			throw new IllegalArgumentException("itemAmount must not be negative");
		}
		if (itemAmount == 0) {
			return new OrderAmounts(0, 0, 0, 0);
		}
		long discountAmount = itemAmount / 100;
		long shippingFee = itemAmount >= FREE_SHIPPING_THRESHOLD ? 0 : SHIPPING_FEE;
		try {
			long totalAmount = Math.addExact(Math.subtractExact(itemAmount, discountAmount), shippingFee);
			return new OrderAmounts(itemAmount, discountAmount, shippingFee, totalAmount);
		} catch (ArithmeticException e) {
			throw new BusinessException(ErrorCode.ORDER_AMOUNT_TOO_LARGE);
		}
	}
}
