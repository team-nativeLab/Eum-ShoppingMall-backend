package com.example.shopping.domain.product.dto.request;

import java.util.List;

import com.example.shopping.domain.product.entity.Certification;
import com.example.shopping.domain.product.entity.ProductDescriptionFormat;
import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 보낸 항목만 수정한다. 설명·이미지·브랜드는 빈 문자열을, 정가는 0을 보내면 지운다.
 * 가격 공개 여부는 금액 미표시 전환 API로 바꾼다.
 *
 * @param categoryId        카테고리 ID
 * @param name              상품명(200자 이하)
 * @param description       상품 설명(20,000자 이하). 빈 문자열이면 지운다.
 * @param imageUrl          대표 이미지 주소. 빈 문자열이면 지운다.
 * @param basePrice         대표 판매가(원). 목록·상세의 가격 표시, 가격순 정렬, 할인 표시(정가와 비교)에 쓴다. 주문 단가는 옵션 가격이다.
 * @param listPrice         정가(원). 0이면 지운다.
 * @param brand             브랜드 이름(100자 이하). 빈 문자열이면 지운다.
 * @param certifications    보낸 목록으로 통째로 바꾼다. 빈 목록을 보내면 인증을 모두 지운다.
 * @param recommendScore    추천순 정렬 점수(0~100만). 클수록 앞
 * @param status            판매 상태. ON_SALE(판매중), SOLD_OUT(품절), HIDDEN(비공개), DELETED(삭제)
 * @param priceVisible      금액 공개 여부. false면 금액을 숨기고 견적 문의로만 받는다. 생략하면 그대로 둔다.
 * @param productCode       상품 코드. 빈 문자열이면 지운다. 다른 상품과 겹치면 409(PRODUCT_CODE_DUPLICATED)
 * @param modelName         모델명(100자 이하). 빈 문자열이면 지운다.
 * @param summary           한 줄 요약(300자 이하). 빈 문자열이면 지운다.
 * @param countryOfOrigin   원산지(50자 이하). 빈 문자열이면 지운다.
 * @param shippingEstimate  배송 예정 안내(100자 이하). 빈 문자열이면 지운다.
 * @param descriptionFormat 상품 설명 서식. PLAIN(평문), RICH_V1(제한된 서식)
 * @param images            상세 갤러리. 보내면 통째로 바꾸고 첫 이미지가 대표 이미지(imageUrl)가 된다(빈 목록은 둘 다 지움). 나머지 상세 정보는
 *                          ProductDetailFields 참고
 * @param specifications    사양 표(20줄까지). 보내면 통째로 바꾸고, 빈 목록이면 모두 지운다.
 * @param featured          베스트 상품 목록(?collection=featured)에 넣을지(#207). 생략하면 그대로
 * @param todayDeal         오늘의 상품 목록(?collection=today)에 넣을지(#207). 생략하면 그대로
 * @param supplierId        발주처 ID(#208). 없는 발주처는 404. 생략하면 그대로
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

		Boolean priceVisible,

		@Pattern(regexp = ProductDetailFields.PRODUCT_CODE, message = "상품 코드는 영문·숫자로 시작하고 영문·숫자·-·_ 50자 이내입니다.")
		String productCode,

		@Size(max = 100, message = "모델명은 100자 이하여야 합니다.")
		String modelName,

		@Size(max = 300, message = "요약은 300자 이하여야 합니다.")
		String summary,

		@Size(max = 50, message = "원산지는 50자 이하여야 합니다.")
		String countryOfOrigin,

		@Size(max = 100, message = "배송 예정 안내는 100자 이하여야 합니다.")
		String shippingEstimate,

		ProductDescriptionFormat descriptionFormat,

		@Size(max = 10, message = "상세 이미지는 10장까지입니다.")
		List<@NotNull(message = "이미지를 확인해 주세요.") @Valid ProductImageRequest> images,

		@Size(max = 20, message = "사양은 20줄까지입니다.")
		List<@NotNull(message = "사양을 확인해 주세요.") @Valid ProductSpecificationRequest> specifications,

		Boolean featured,

		Boolean todayDeal,

		@Positive(message = "발주처를 확인해 주세요.")
		Long supplierId)
		implements ProductDetailFields {
}
