package com.example.shopping.domain.product.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductDescriptionFormat;
import com.example.shopping.domain.product.entity.ProductSku;
import com.example.shopping.domain.product.entity.ProductStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자에게는 금액 미표시 상품의 가격과 옵션별 재고 수량도 그대로 보여준다.
 *
 * @param id                상품 ID
 * @param categoryId        카테고리 ID. 없으면 null
 * @param name              상품명
 * @param brand             브랜드 이름. 없으면 null
 * @param description       상품 설명. 서식은 descriptionFormat
 * @param imageUrl          대표 이미지. 없으면 null
 * @param basePrice         대표 판매가(원). 목록·상세의 가격 표시, 가격순 정렬, 할인 표시(정가와 비교)에 쓴다. 주문 단가는 옵션 가격이다.
 * @param listPrice         정가. 없으면 null
 * @param priceVisible      금액 공개 여부. false면 고객에게 금액을 숨기고 견적 문의로 받는다.
 * @param certifications    받은 안전 인증
 * @param recommendScore    추천순 정렬 점수 (클수록 앞)
 * @param salesCount        누적 판매 수량 (인기순 정렬 기준)
 * @param status            판매 상태. ON_SALE(판매중), SOLD_OUT(품절), HIDDEN(비공개), DELETED(삭제)
 * @param skus              옵션 목록. 삭제(판매 중지)한 옵션도 들어 있다.
 * @param createdAt         등록 시각
 * @param updatedAt         마지막 수정 시각
 * @param productCode       상품 코드. 없으면 null
 * @param modelName         모델명. 없으면 null
 * @param summary           한 줄 요약. 없으면 null
 * @param countryOfOrigin   원산지. 없으면 null
 * @param shippingEstimate  배송 예정 안내. 없으면 null
 * @param descriptionFormat 상품 설명 서식. PLAIN(평문), RICH_V1(제한된 서식)
 * @param images            상세 갤러리. 첫 이미지가 대표 이미지(imageUrl)다.
 * @param specifications    사양 표(순서대로)
 * @param featured          베스트 상품으로 지정했는지
 * @param todayDeal         오늘의 상품으로 지정했는지
 * @param supplierId        발주처 ID. 없으면 null
 */
public record AdminProductDetailResponse(Long id, Long categoryId, String name, String brand, String description,
		String imageUrl, long basePrice, Long listPrice, boolean priceVisible,
		List<CertificationResponse> certifications, int recommendScore, long salesCount, ProductStatus status,
		List<SkuResponse> skus, LocalDateTime createdAt, LocalDateTime updatedAt, String productCode,
		String modelName, String summary, String countryOfOrigin, String shippingEstimate,
		ProductDescriptionFormat descriptionFormat, List<ProductDetailInfo.Image> images,
		List<ProductDetailInfo.Specification> specifications, boolean featured, boolean todayDeal,
		Long supplierId) {

	/**
	 * 관리자 상품 옵션
	 *
	 * @param id         옵션(SKU) ID
	 * @param optionName 옵션 이름
	 * @param price      옵션 판매가(원). 기본가에 더하는 금액이 아니라 장바구니·주문에 그대로 쓰는 최종 단가다.
	 * @param stock      재고 수량
	 * @param active     판매하는 옵션인지. 옵션을 삭제하면 false
	 */
	@Schema(name = "AdminProductSkuResponse")
	public record SkuResponse(Long id, String optionName, long price, int stock, boolean active) {

		static SkuResponse from(ProductSku sku) {
			return new SkuResponse(sku.getId(), sku.getOptionName(), sku.getPrice(), sku.getStock(), sku.isActive());
		}
	}

	public static AdminProductDetailResponse from(Product product) {
		Long categoryId = product.getCategory() == null ? null : product.getCategory().getId();
		return new AdminProductDetailResponse(product.getId(), categoryId, product.getName(), product.getBrand(),
				product.getDescription(), product.getImageUrl(), product.getBasePrice(), product.getListPrice(),
				product.isPriceVisible(), CertificationResponse.listOf(product.getCertifications()),
				product.getRecommendScore(), product.getSalesCount(),
				product.getStatus(), product.getSkus().stream().map(SkuResponse::from).toList(),
				product.getCreatedAt(), product.getUpdatedAt(), product.getProductCode(), product.getModelName(),
				product.getSummary(), product.getCountryOfOrigin(), product.getShippingEstimate(),
				product.getDescriptionFormat(), ProductDetailInfo.images(product),
				ProductDetailInfo.specifications(product), product.isFeatured(), product.isTodayDeal(),
				product.getSupplierId());
	}
}
