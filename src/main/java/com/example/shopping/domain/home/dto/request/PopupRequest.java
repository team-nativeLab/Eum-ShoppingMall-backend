package com.example.shopping.domain.home.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 팝업 등록·수정(#211). 수정도 모든 값을 보낸다.
 *
 * @param imageUrl  업로드 API가 돌려준 주소나 웹의 정적 이미지(/assets/...). 사진이 없으면 null이나 빈 문자열
 * @param visible   웹에 띄울지. 없으면 true
 * @param sortOrder 작을수록 앞. 같으면 최근 등록한 팝업이 앞이다. 없으면 0
 */
public record PopupRequest(
		@NotBlank(message = "팝업 제목을 입력해 주세요.")
		@Size(max = 100, message = "팝업 제목은 100자 이하여야 합니다.")
		String title,

		@Size(max = 500, message = "사진 주소가 너무 깁니다.")
		String imageUrl,

		@NotBlank(message = "팝업 내용을 입력해 주세요.")
		@Size(max = 2000, message = "팝업 내용은 2,000자 이하여야 합니다.")
		String body,

		Boolean visible,

		@Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
		@Max(value = 9999, message = "정렬 순서는 9999 이하여야 합니다.")
		Integer sortOrder) {
}
