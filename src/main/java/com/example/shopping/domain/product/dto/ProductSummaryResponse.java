package com.example.shopping.domain.product.dto;

import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductStatus;

/** price는 금액 미표시 상품이면 null */
public record ProductSummaryResponse(Long id, String name, String brand, String imageUrl, Long price,
		boolean priceVisible, boolean safetyCertified, ProductStatus status) {

	public static ProductSummaryResponse from(Product product) {
		return new ProductSummaryResponse(product.getId(), product.getName(), product.getBrand(),
				product.getImageUrl(), product.getDisplayPrice(), product.isPriceVisible(),
				product.isSafetyCertified(), product.getStatus());
	}
}
