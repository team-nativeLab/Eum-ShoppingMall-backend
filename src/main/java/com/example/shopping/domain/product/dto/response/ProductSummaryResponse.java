package com.example.shopping.domain.product.dto.response;

import java.util.List;

import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductStatus;

/**
 * 상품 목록의 한 칸. 금액 미표시 상품이면 price와 listPrice가 null이다.
 *
 * @param listPrice      정가. 판매가(price)보다 높을 때만 할인 전 가격으로 보여 주면 된다. 없으면 null
 * @param soldOut        품절이면 true (품절로 지정했거나 모든 옵션의 재고가 없음)
 * @param optionRequired 옵션이 여러 개라 상세에서 옵션을 골라야 하면 true
 * @param defaultSkuId   옵션이 하나뿐이면 그 옵션 ID. 목록에서 바로 장바구니에 담을 때 쓴다. 아니면 null
 */
public record ProductSummaryResponse(Long id, Long categoryId, String name, String brand, String imageUrl,
		Long price, Long listPrice, boolean priceVisible, List<CertificationResponse> certifications,
		ProductStatus status, boolean soldOut, boolean optionRequired, Long defaultSkuId) {

	public static ProductSummaryResponse from(Product product) {
		Long categoryId = product.getCategory() == null ? null : product.getCategory().getId();
		return new ProductSummaryResponse(product.getId(), categoryId, product.getName(), product.getBrand(),
				product.getImageUrl(), product.getDisplayPrice(), product.getDisplayListPrice(),
				product.isPriceVisible(), CertificationResponse.listOf(product.getCertifications()),
				product.getStatus(), product.isSoldOut(), product.isOptionRequired(), product.getDefaultSkuId());
	}
}
