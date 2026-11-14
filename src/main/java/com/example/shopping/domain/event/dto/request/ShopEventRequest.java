package com.example.shopping.domain.event.dto.request;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 이벤트 등록·수정(전체 수정). 비워 둘 수 있는 글은 빈 문자열로 저장한다.
 *
 * @param subtitle        목록 카드의 부제
 * @param eyebrow         상세 제목 위 작은 문구
 * @param heroTitle       상세 화면 큰 제목 줄(1~3줄)
 * @param heroDescription 상세 제목 아래 설명
 * @param sections        상세 본문 구성(소제목·본문, 10개까지). 없으면 빈 배열
 * @param searchKeyword   관련 상품을 찾을 검색어
 * @param startAt         시작 시각. 시간대를 포함한 ISO 8601(예: 2026-10-01T00:00:00+09:00)
 * @param endAt           종료 시각(이 시각까지 진행 중). 시작 시각보다 앞설 수 없다.
 * @param visible         고객 화면에 보일지. 없으면 true
 * @param sortOrder       작을수록 앞. 없으면 0
 */
public record ShopEventRequest(
		@NotBlank(message = "이벤트명을 입력해 주세요.")
		@Size(max = 100, message = "이벤트명은 100자 이하여야 합니다.")
		String title,

		@Size(max = 200, message = "부제는 200자 이하여야 합니다.")
		String subtitle,

		@Size(max = 50, message = "작은 문구는 50자 이하여야 합니다.")
		String eyebrow,

		@NotEmpty(message = "상세 제목을 입력해 주세요.")
		@Size(max = 3, message = "상세 제목은 3줄까지입니다.")
		List<@NotBlank(message = "빈 제목 줄은 넣을 수 없습니다.")
		@Size(max = 50, message = "상세 제목 한 줄은 50자 이하여야 합니다.")
		@Pattern(regexp = "^[^\\r\\n]*$", message = "상세 제목 한 줄에는 줄바꿈을 넣을 수 없습니다.") String> heroTitle,

		@Size(max = 500, message = "상세 설명은 500자 이하여야 합니다.")
		String heroDescription,

		@Size(max = 5000, message = "본문은 5,000자 이하여야 합니다.")
		String body,

		@Size(max = 10, message = "본문 구성은 10개까지입니다.")
		List<@NotNull @Valid Section> sections,

		@Size(max = 50, message = "검색어는 50자 이하여야 합니다.")
		String searchKeyword,

		@NotNull(message = "시작 시각을 입력해 주세요.")
		OffsetDateTime startAt,

		@NotNull(message = "종료 시각을 입력해 주세요.")
		OffsetDateTime endAt,

		Boolean visible,

		@Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
		@Max(value = 9999, message = "정렬 순서는 9999 이하여야 합니다.")
		Integer sortOrder) {

	/** 상세 본문의 한 구성 */
	public record Section(
			@NotBlank(message = "소제목을 입력해 주세요.")
			@Size(max = 100, message = "소제목은 100자 이하여야 합니다.")
			String heading,

			@NotBlank(message = "구성 본문을 입력해 주세요.")
			@Size(max = 2000, message = "구성 본문은 2,000자 이하여야 합니다.")
			String body) {
	}
}
