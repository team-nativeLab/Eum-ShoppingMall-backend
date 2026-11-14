package com.example.shopping.domain.product.dto.response;

import java.util.List;

import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductDescriptionFormat;
import com.example.shopping.domain.product.entity.ProductSku;
import com.example.shopping.domain.product.entity.ProductStatus;

/**
 * price·listPrice는 금액 미표시 상품이면 null (옵션 가격도 함께 숨긴다)
 *
 * @param listPrice         정가. 없으면 null
 * @param soldOut           품절이면 true (품절로 지정했거나 모든 옵션의 재고가 없음)
 * @param rating            보이는 리뷰의 평균 별점(소수 첫째 자리). 리뷰가 없으면 null
 * @param imageUrl          대표 이미지. 갤러리가 있으면 첫 이미지와 같다.
 * @param descriptionFormat 설명 서식(PLAIN, RICH_V1). 화면은 어느 쪽이든 텍스트로만 그린다.
 * @param images            상세 갤러리(순서대로). 없으면 빈 배열
 * @param specifications    사양 표(순서대로). 없으면 빈 배열
 */
public record ProductDetailResponse(Long id, Long categoryId, String name, String brand, String description,
		String imageUrl, Long price, Long listPrice, boolean priceVisible,
		List<CertificationResponse> certifications, ProductStatus status, boolean soldOut, List<SkuResponse> skus,
		Double rating, int reviewCount, String modelName, String summary, String countryOfOrigin,
		String shippingEstimate, ProductDescriptionFormat descriptionFormat, List<ProductDetailInfo.Image> images,
		List<ProductDetailInfo.Specification> specifications) {

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
				ProductDetailInfo.images(product), ProductDetailInfo.specifications(product));
	}
}
