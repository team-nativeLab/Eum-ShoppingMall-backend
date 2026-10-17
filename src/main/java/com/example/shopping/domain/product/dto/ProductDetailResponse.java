package com.example.shopping.domain.product.dto;

import java.util.List;

import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductSku;
import com.example.shopping.domain.product.entity.ProductStatus;

/**
 * price·listPrice는 금액 미표시 상품이면 null (옵션 가격도 함께 숨긴다)
 *
 * @param listPrice 정가. 없으면 null
 * @param soldOut   품절이면 true (품절로 지정했거나 모든 옵션의 재고가 없음)
 */
public record ProductDetailResponse(Long id, Long categoryId, String name, String brand, String description,
		String imageUrl, Long price, Long listPrice, boolean priceVisible,
		List<CertificationResponse> certifications, ProductStatus status, boolean soldOut, List<SkuResponse> skus) {

	public record SkuResponse(Long id, String optionName, Long price, boolean soldOut) {

		static SkuResponse of(ProductSku sku, boolean priceVisible) {
			return new SkuResponse(sku.getId(), sku.getOptionName(), priceVisible ? sku.getPrice() : null,
					sku.getStock() <= 0);
		}
	}

	public static ProductDetailResponse from(Product product) {
		List<SkuResponse> skus = product.getActiveSkus().stream()
				.map(sku -> SkuResponse.of(sku, product.isPriceVisible()))
				.toList();
		Long categoryId = product.getCategory() == null ? null : product.getCategory().getId();
		return new ProductDetailResponse(product.getId(), categoryId, product.getName(), product.getBrand(),
				product.getDescription(), product.getImageUrl(), product.getDisplayPrice(),
				product.getDisplayListPrice(), product.isPriceVisible(),
				CertificationResponse.listOf(product.getCertifications()), product.getStatus(), product.isSoldOut(),
				skus);
	}
}
