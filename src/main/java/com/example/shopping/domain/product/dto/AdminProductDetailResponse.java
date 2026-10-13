package com.example.shopping.domain.product.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductSku;
import com.example.shopping.domain.product.entity.ProductStatus;

/**
 * 관리자에게는 금액 미표시 상품의 가격과 옵션별 재고 수량도 그대로 보여준다.
 *
 * @param recommendScore 추천순 정렬 점수 (클수록 앞)
 * @param salesCount     누적 판매 수량 (인기순 정렬 기준)
 */
public record AdminProductDetailResponse(Long id, Long categoryId, String name, String brand, String description,
		String imageUrl, long basePrice, boolean priceVisible, boolean safetyCertified, int recommendScore,
		long salesCount, ProductStatus status, List<SkuResponse> skus, LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	public record SkuResponse(Long id, String optionName, long price, int stock) {

		static SkuResponse from(ProductSku sku) {
			return new SkuResponse(sku.getId(), sku.getOptionName(), sku.getPrice(), sku.getStock());
		}
	}

	public static AdminProductDetailResponse from(Product product) {
		Long categoryId = product.getCategory() == null ? null : product.getCategory().getId();
		return new AdminProductDetailResponse(product.getId(), categoryId, product.getName(), product.getBrand(),
				product.getDescription(), product.getImageUrl(), product.getBasePrice(), product.isPriceVisible(),
				product.isSafetyCertified(), product.getRecommendScore(), product.getSalesCount(),
				product.getStatus(), product.getSkus().stream().map(SkuResponse::from).toList(),
				product.getCreatedAt(), product.getUpdatedAt());
	}
}
