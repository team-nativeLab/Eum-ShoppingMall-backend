package com.example.shopping.domain.product.dto;

import java.util.List;

import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductSku;
import com.example.shopping.domain.product.entity.ProductStatus;

/** price는 금액 미표시 상품이면 null (옵션 가격도 함께 숨긴다) */
public record ProductDetailResponse(Long id, String name, String brand, String description, String imageUrl,
		Long price, boolean priceVisible, boolean safetyCertified, ProductStatus status, List<SkuResponse> skus) {

	public record SkuResponse(Long id, String optionName, Long price, boolean soldOut) {

		static SkuResponse of(ProductSku sku, boolean priceVisible) {
			return new SkuResponse(sku.getId(), sku.getOptionName(), priceVisible ? sku.getPrice() : null,
					sku.getStock() <= 0);
		}
	}

	public static ProductDetailResponse from(Product product) {
		List<SkuResponse> skus = product.getSkus().stream()
				.map(sku -> SkuResponse.of(sku, product.isPriceVisible()))
				.toList();
		return new ProductDetailResponse(product.getId(), product.getName(), product.getBrand(),
				product.getDescription(), product.getImageUrl(), product.getDisplayPrice(), product.isPriceVisible(),
				product.isSafetyCertified(), product.getStatus(), skus);
	}
}
