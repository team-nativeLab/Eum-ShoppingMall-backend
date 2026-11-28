package com.example.shopping.domain.product.dto.response;

import java.util.List;

import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductDescriptionFormat;
import com.example.shopping.domain.product.entity.ProductSku;
import com.example.shopping.domain.product.entity.ProductStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * price·listPrice는 금액 미표시 상품이면 null (옵션 가격도 함께 숨긴다)
 *
 * @param id                상품 ID
 * @param categoryId        카테고리 ID. 없으면 null
 * @param name              상품명
 * @param brand             브랜드 이름. 없으면 null
 * @param description       상품 설명. 서식은 descriptionFormat
 * @param imageUrl          대표 이미지. 갤러리가 있으면 첫 이미지와 같다.
 * @param price             대표 판매가(원). 주문 단가는 고른 옵션의 가격(skus[].price)이다. 금액 미표시 상품이면 null
 * @param listPrice         정가. 없으면 null
 * @param priceVisible      금액 공개 여부. false면 주문 대신 견적 문의로 안내한다.
 * @param certifications    받은 안전 인증
 * @param status            판매 상태. ON_SALE(판매중), SOLD_OUT(품절)
 * @param soldOut           품절이면 true (품절로 지정했거나 모든 옵션의 재고가 없음)
 * @param skus              판매하는 옵션 목록. 장바구니 담기·주문에 옵션 ID(skuId)를 보낸다.
 * @param rating            보이는 리뷰의 평균 별점(소수 첫째 자리). 리뷰가 없으면 null
 * @param reviewCount       보이는 리뷰 수
 * @param modelName         모델명. 없으면 null
 * @param summary           한 줄 요약. 없으면 null
 * @param countryOfOrigin   원산지. 없으면 null
 * @param shippingEstimate  배송 예정 안내(예: 평일 기준 2~3일 이내 출고). 없으면 null
 * @param descriptionFormat 설명 서식(PLAIN, RICH_V1). 화면은 어느 쪽이든 텍스트로만 그린다.
 * @param images            상세 갤러리(순서대로). 없으면 빈 배열
 * @param specifications    사양 표(순서대로). 없으면 빈 배열
 * @param featured          베스트 상품으로 지정했는지(#207)
 * @param todayDeal         오늘의 상품으로 지정했는지(#207)
 */
public record ProductDetailResponse(Long id, Long categoryId, String name, String brand, String description,
		String imageUrl, Long price, Long listPrice, boolean priceVisible,
		List<CertificationResponse> certifications, ProductStatus status, boolean soldOut, List<SkuResponse> skus,
		Double rating, int reviewCount, String modelName, String summary, String countryOfOrigin,
		String shippingEstimate, ProductDescriptionFormat descriptionFormat, List<ProductDetailInfo.Image> images,
		List<ProductDetailInfo.Specification> specifications, boolean featured, boolean todayDeal) {

	/**
	 * 상품 옵션
	 *
	 * @param id         옵션(SKU) ID. 장바구니 담기·주문에 보낸다.
	 * @param optionName 옵션 이름
	 * @param price      옵션 판매가(원). 기본가에 더하는 금액이 아니라 장바구니·주문에 그대로 쓰는 최종 단가다. 금액 미표시 상품이면 null
	 * @param soldOut    재고가 없는지
	 */
	@Schema(name = "ProductSkuResponse")
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
				skus, product.getRating(), product.getReviewCount(), product.getModelName(), product.getSummary(),
				product.getCountryOfOrigin(), product.getShippingEstimate(), product.getDescriptionFormat(),
				ProductDetailInfo.images(product), ProductDetailInfo.specifications(product), product.isFeatured(),
				product.isTodayDeal());
	}
}
