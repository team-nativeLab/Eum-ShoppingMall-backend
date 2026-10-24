package com.example.shopping.domain.product.dto.response;

import com.example.shopping.domain.product.entity.Product;

/** 검색창 자동 완성의 한 줄 */
public record ProductSuggestionResponse(Long productId, String name, String brand) {

	public static ProductSuggestionResponse from(Product product) {
		return new ProductSuggestionResponse(product.getId(), product.getName(), product.getBrand());
	}
}
