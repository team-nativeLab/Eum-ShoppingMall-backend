package com.example.shopping.domain.product.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductStatus;

/**
 * 관리자에게는 금액 미표시 상품의 가격도 그대로 보여준다.
 *
 * @param id             상품 ID
 * @param categoryId     카테고리 ID. 없으면 null
 * @param name           상품명
 * @param brand          브랜드 이름. 없으면 null
 * @param imageUrl       대표 이미지. 없으면 null
 * @param basePrice      대표 판매가(원). 목록·상세의 가격 표시, 가격순 정렬, 할인 표시(정가와 비교)에 쓴다. 주문 단가는 옵션 가격이다.
 * @param listPrice      정가(원). 없으면 null
 * @param priceVisible   금액 공개 여부
 * @param certifications 받은 안전 인증
 * @param recommendScore 추천순 정렬 점수. 클수록 앞
 * @param salesCount     누적 판매 수량
 * @param status         판매 상태. ON_SALE(판매중), SOLD_OUT(품절), HIDDEN(비공개), DELETED(삭제)
 * @param createdAt      등록 시각
 * @param productCode    상품 코드. 없으면 null
 * @param featured       베스트 상품으로 지정했는지
 * @param todayDeal      오늘의 상품으로 지정했는지
 * @param supplierId     발주처 ID. 없으면 null
 */
public record AdminProductSummaryResponse(Long id, Long categoryId, String name, String brand, String imageUrl,
		long basePrice, Long listPrice, boolean priceVisible, List<CertificationResponse> certifications,
		int recommendScore, long salesCount, ProductStatus status, LocalDateTime createdAt, String productCode,
		boolean featured, boolean todayDeal, Long supplierId) {

	public static AdminProductSummaryResponse from(Product product) {
		Long categoryId = product.getCategory() == null ? null : product.getCategory().getId();
		return new AdminProductSummaryResponse(product.getId(), categoryId, product.getName(), product.getBrand(),
				product.getImageUrl(), product.getBasePrice(), product.getListPrice(), product.isPriceVisible(),
				CertificationResponse.listOf(product.getCertifications()), product.getRecommendScore(),
				product.getSalesCount(), product.getStatus(), product.getCreatedAt(), product.getProductCode(),
				product.isFeatured(), product.isTodayDeal(), product.getSupplierId());
	}
}
