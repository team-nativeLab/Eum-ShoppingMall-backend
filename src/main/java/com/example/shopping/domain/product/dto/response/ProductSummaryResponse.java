package com.example.shopping.domain.product.dto.response;

import java.util.List;

import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductStatus;

/**
 * 상품 목록의 한 칸. 금액 미표시 상품이면 price와 listPrice가 null이다.
 *
 * @param id             상품 ID
 * @param categoryId     카테고리 ID. 없으면 null
 * @param name           상품명
 * @param brand          브랜드 이름. 없으면 null
 * @param imageUrl       대표 이미지. 없으면 null
 * @param price          대표 판매가(원). 주문 단가는 옵션 가격이다. 금액 미표시 상품이면 null
 * @param listPrice      정가. 판매가(price)보다 높을 때만 할인 전 가격으로 보여 주면 된다. 없으면 null
 * @param priceVisible   금액 공개 여부. false면 견적 문의로 안내한다.
 * @param certifications 받은 안전 인증
 * @param status         판매 상태. ON_SALE(판매중), SOLD_OUT(품절)
 * @param soldOut        품절이면 true (품절로 지정했거나 모든 옵션의 재고가 없음)
 * @param optionRequired 옵션이 여러 개라 상세에서 옵션을 골라야 하면 true
 * @param defaultSkuId   옵션이 하나뿐이면 그 옵션 ID. 목록에서 바로 장바구니에 담을 때 쓴다. 아니면 null
 * @param rating         보이는 리뷰의 평균 별점(소수 첫째 자리). 리뷰가 없으면 null
 * @param reviewCount    보이는 리뷰 수
 * @param featured       베스트 상품으로 지정했는지(#207)
 * @param todayDeal      오늘의 상품으로 지정했는지(#207)
 */
public record ProductSummaryResponse(Long id, Long categoryId, String name, String brand, String imageUrl,
		Long price, Long listPrice, boolean priceVisible, List<CertificationResponse> certifications,
		ProductStatus status, boolean soldOut, boolean optionRequired, Long defaultSkuId, Double rating,
		int reviewCount, boolean featured, boolean todayDeal) {

	public static ProductSummaryResponse from(Product product) {
		Long categoryId = product.getCategory() == null ? null : product.getCategory().getId();
		return new ProductSummaryResponse(product.getId(), categoryId, product.getName(), product.getBrand(),
				product.getImageUrl(), product.getDisplayPrice(), product.getDisplayListPrice(),
				product.isPriceVisible(), CertificationResponse.listOf(product.getCertifications()),
				product.getStatus(), product.isSoldOut(), product.isOptionRequired(), product.getDefaultSkuId(),
				product.getRating(), product.getReviewCount(), product.isFeatured(), product.isTodayDeal());
	}
}
