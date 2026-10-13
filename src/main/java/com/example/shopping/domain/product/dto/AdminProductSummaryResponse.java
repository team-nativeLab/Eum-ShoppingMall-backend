package com.example.shopping.domain.product.dto;

import java.time.LocalDateTime;

import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductStatus;

/** 관리자에게는 금액 미표시 상품의 가격도 그대로 보여준다. */
public record AdminProductSummaryResponse(Long id, Long categoryId, String name, String brand, String imageUrl,
		long basePrice, boolean priceVisible, boolean safetyCertified, int recommendScore, long salesCount,
		ProductStatus status, LocalDateTime createdAt) {

	public static AdminProductSummaryResponse from(Product product) {
		Long categoryId = product.getCategory() == null ? null : product.getCategory().getId();
		return new AdminProductSummaryResponse(product.getId(), categoryId, product.getName(), product.getBrand(),
				product.getImageUrl(), product.getBasePrice(), product.isPriceVisible(), product.isSafetyCertified(),
				product.getRecommendScore(), product.getSalesCount(), product.getStatus(), product.getCreatedAt());
	}
}
