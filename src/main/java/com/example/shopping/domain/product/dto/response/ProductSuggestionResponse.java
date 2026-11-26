package com.example.shopping.domain.product.dto.response;

import com.example.shopping.domain.product.entity.Product;

/**
 * 검색창 자동 완성의 한 줄
 *
 * @param productId 상품 ID. 누르면 상품 상세로 간다.
 * @param name      상품명
 * @param brand     브랜드 이름. 없으면 null
 */
public record ProductSuggestionResponse(Long productId, String name, String brand) {

	public static ProductSuggestionResponse from(Product product) {
		return new ProductSuggestionResponse(product.getId(), product.getName(), product.getBrand());
	}
}
