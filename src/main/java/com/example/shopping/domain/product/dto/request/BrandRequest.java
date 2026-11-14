package com.example.shopping.domain.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 브랜드 등록·수정. 빈 문자열은 값 없음(null)으로 저장한다.
 *
 * @param name          상품의 brand 값과 같은 이름. 바꾸면 이 이름을 쓰던 상품의 브랜드도 함께 바꾼다.
 * @param logoUrl       업로드 API가 돌려준 주소만 받는다.
 * @param coverImageUrl 업로드 API가 돌려준 주소만 받는다.
 * @param visible       고객 화면에 보일지. 없으면 true
 * @param featured      브랜드 목록 위의 대표 브랜드로 쓸지. 여러 개면 정렬 순서가 앞선 하나를 쓴다. 없으면 false
 */
public record BrandRequest(
		@NotBlank(message = "브랜드 이름을 입력해 주세요.")
		@Size(max = 100, message = "브랜드 이름은 100자 이하여야 합니다.")
		String name,

		@Size(max = 100, message = "영문 이름은 100자 이하여야 합니다.")
		String englishName,

		@Size(max = 500, message = "로고 주소가 너무 깁니다.")
		String logoUrl,

		@Size(max = 200, message = "한 줄 소개는 200자 이하여야 합니다.")
		String tagline,

		@Size(max = 500, message = "대표 이미지 주소가 너무 깁니다.")
		String coverImageUrl,

		@Size(max = 100, message = "주력 분야는 100자 이하여야 합니다.")
		String mainField,

		@Size(max = 50, message = "원산지는 50자 이하여야 합니다.")
		String originCountry,

		@Size(max = 200, message = "인증 문구는 200자 이하여야 합니다.")
		String certification,

		@Size(max = 100, message = "브랜드 스토리 제목은 100자 이하여야 합니다.")
		String storyTitle,

		@Size(max = 5000, message = "브랜드 스토리는 5,000자 이하여야 합니다.")
		String storyBody,

		Boolean visible,

		Boolean featured,

		@Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
		@Max(value = 9999, message = "정렬 순서는 9999 이하여야 합니다.")
		Integer sortOrder) {
}
