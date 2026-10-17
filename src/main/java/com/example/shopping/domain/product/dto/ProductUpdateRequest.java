package com.example.shopping.domain.product.dto;

import java.util.List;

import com.example.shopping.domain.product.entity.Certification;
import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 보낸 항목만 수정한다. 설명·이미지·브랜드는 빈 문자열을, 정가는 0을 보내면 지운다.
 * 가격 공개 여부는 금액 미표시 전환 API로 바꾼다.
 *
 * @param certifications 보낸 목록으로 통째로 바꾼다. 빈 목록을 보내면 인증을 모두 지운다.
 * @param skus 기존 클라이언트와의 요청 호환 입력으로 받지만 무시한다. 옵션 변경은 전용 SKU API를 사용한다.
 */
public record ProductUpdateRequest(
		Long categoryId,

		@Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "상품명을 입력해 주세요.")
		@Size(max = 200, message = "상품명은 200자 이하여야 합니다.")
		String name,

		@Size(max = 20000, message = "상품 설명은 20000자 이하여야 합니다.")
		String description,

		@Pattern(regexp = ValidationPatterns.URL_OR_PATH_OR_EMPTY, message = "이미지 주소 형식이 올바르지 않습니다.")
		@Size(max = 500, message = "이미지 주소는 500자 이하여야 합니다.")
		String imageUrl,

		@PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
		@Max(value = ValidationPatterns.MAX_PRICE, message = "가격이 너무 큽니다.")
		Long basePrice,

		@PositiveOrZero(message = "정가는 0 이상이어야 합니다.")
		@Max(value = ValidationPatterns.MAX_PRICE, message = "정가가 너무 큽니다.")
		Long listPrice,

		@Size(max = 100, message = "브랜드는 100자 이하여야 합니다.")
		String brand,

		@Size(max = 10, message = "인증은 10개 이하로 지정해 주세요.")
		List<@NotNull(message = "인증 값을 확인해 주세요.") Certification> certifications,

		@Min(value = 0, message = "추천 점수는 0 이상이어야 합니다.")
		@Max(value = 1_000_000, message = "추천 점수는 100만 이하여야 합니다.")
		Integer recommendScore,

		ProductStatus status,

		List<@Valid ProductSkuRequest> skus) {
}
