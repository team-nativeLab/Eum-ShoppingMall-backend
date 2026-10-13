package com.example.shopping.domain.product.dto;

import java.util.List;

import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * @param priceVisible    생략하면 가격 공개(true)
 * @param brand           브랜드 이름. 브랜드 필터에 쓰인다.
 * @param safetyCertified 안전 인증 제품이면 true. 생략하면 false
 * @param recommendScore  추천순 정렬 점수. 클수록 앞에 나온다. 생략하면 0
 * @param status          생략하면 판매중(ON_SALE)
 * @param skus            옵션 목록. 옵션이 없는 상품이면 생략
 */
public record ProductCreateRequest(
		Long categoryId,

		@NotBlank(message = "상품명을 입력해 주세요.")
		@Size(max = 200, message = "상품명은 200자 이하여야 합니다.")
		String name,

		@Size(max = 20000, message = "상품 설명은 20000자 이하여야 합니다.")
		String description,

		@Pattern(regexp = ValidationPatterns.URL_OR_PATH, message = "이미지 주소 형식이 올바르지 않습니다.")
		@Size(max = 500, message = "이미지 주소는 500자 이하여야 합니다.")
		String imageUrl,

		@NotNull(message = "가격을 입력해 주세요.")
		@PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
		@Max(value = ValidationPatterns.MAX_PRICE, message = "가격이 너무 큽니다.")
		Long basePrice,

		Boolean priceVisible,

		@Size(max = 100, message = "브랜드는 100자 이하여야 합니다.")
		String brand,

		Boolean safetyCertified,

		@Min(value = 0, message = "추천 점수는 0 이상이어야 합니다.")
		@Max(value = 1_000_000, message = "추천 점수는 100만 이하여야 합니다.")
		Integer recommendScore,

		ProductStatus status,

		List<@Valid ProductSkuRequest> skus) {
}
