package com.example.shopping.domain.home.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 홈 화면 편성. 관리자가 통째로 저장하고(PUT /api/v1/admin/home) 저장한 그대로 돌려받는다.
 * 이 모양 그대로 JSON으로 저장하며(site_setting), 고객 홈(GET /api/v1/home)은 베스트 상품 ID 대신 상품 요약을 준다.
 * 모든 목록은 보여 줄 순서대로다. 빈 목록은 그 영역을 그리지 않는다는 뜻이다.
 *
 * @param heroSlides         히어로 슬라이드(최대 5개)
 * @param promoBanners       프로모션 배너(최대 6개)
 * @param serviceBenefits    배송·서비스 안내(최대 8개)
 * @param featuredProductIds 베스트 상품 ID(최대 20개). 겹치면 처음 것만 남긴다. 고객 홈에는 고객에게 보이는 상품만 나간다.
 * @param ctaBand            하단 견적 문의 띠. null이면 그리지 않는다.
 */
public record HomeLayout(
		@NotNull(message = "히어로 슬라이드 목록을 보내 주세요(없으면 빈 배열).")
		@Size(max = 5, message = "히어로 슬라이드는 5개까지입니다.")
		List<@NotNull @Valid HeroSlide> heroSlides,

		@NotNull(message = "프로모션 배너 목록을 보내 주세요(없으면 빈 배열).")
		@Size(max = 6, message = "프로모션 배너는 6개까지입니다.")
		List<@NotNull @Valid PromoBanner> promoBanners,

		@NotNull(message = "서비스 안내 목록을 보내 주세요(없으면 빈 배열).")
		@Size(max = 8, message = "서비스 안내는 8개까지입니다.")
		List<@NotNull @Valid ServiceBenefit> serviceBenefits,

		@NotNull(message = "베스트 상품 목록을 보내 주세요(없으면 빈 배열).")
		@Size(max = 20, message = "베스트 상품은 20개까지입니다.")
		List<@NotNull @Positive Long> featuredProductIds,

		@Valid CtaBand ctaBand) {

	/** 목록 항목의 ID. 관리자 화면이 만들며(예: hero-1731500000000) 같은 목록 안에서 겹치면 안 된다. */
	private static final String ITEM_ID = "^[A-Za-z0-9_-]{1,50}$";

	/** 저장한 적이 없을 때의 편성 */
	public static HomeLayout empty() {
		return new HomeLayout(List.of(), List.of(), List.of(), List.of(), null);
	}

	public HomeLayout withFeaturedProductIds(List<Long> productIds) {
		return new HomeLayout(heroSlides, promoBanners, serviceBenefits, productIds, ctaBand);
	}

	/** 히어로·프로모션 이미지 주소. 저장 전 검사와 참조 없는 이미지 정리에 쓴다. */
	public List<String> imageUrls() {
		List<String> urls = new ArrayList<>();
		heroSlides.forEach(slide -> urls.add(slide.imageUrl()));
		promoBanners.forEach(banner -> urls.add(banner.imageUrl()));
		return urls;
	}

	/** 목록별 항목 ID. 같은 목록 안에서 겹치는지 검사한다. */
	public Stream<List<String>> itemIdGroups() {
		return Stream.of(heroSlides.stream().map(HeroSlide::id).toList(),
				promoBanners.stream().map(PromoBanner::id).toList(),
				serviceBenefits.stream().map(ServiceBenefit::id).toList());
	}

	/**
	 * @param eyebrow      제목 위 작은 문구
	 * @param title        제목 줄(1~3줄)
	 * @param description  부제. 줄바꿈(\n)으로 줄을 나눈다.
	 * @param tagline      데스크톱 오른쪽 위 세로 문구(줄 단위, 없으면 빈 배열)
	 * @param imageUrl     업로드 API가 돌려준 주소나 웹의 정적 이미지(/assets/...)
	 * @param destination  주 버튼이 이동할 사이트 안 경로(/로 시작)
	 * @param ctaLabel     주 버튼 문구
	 * @param secondaryCta 데스크톱에서만 보이는 보조 링크. 없으면 null
	 */
	public record HeroSlide(
			@NotBlank(message = "슬라이드 ID를 입력해 주세요.")
			@Pattern(regexp = ITEM_ID, message = "ID는 영문·숫자·-·_ 50자 이내입니다.")
			String id,

			@Size(max = 50, message = "작은 문구는 50자 이하여야 합니다.")
			String eyebrow,

			@NotEmpty(message = "제목을 입력해 주세요.")
			@Size(max = 3, message = "제목은 3줄까지입니다.")
			List<@NotBlank(message = "빈 제목 줄은 넣을 수 없습니다.") @Size(max = 50, message = "제목 한 줄은 50자 이하여야 합니다.") String> title,

			@Size(max = 300, message = "부제는 300자 이하여야 합니다.")
			String description,

			@Size(max = 6, message = "세로 문구는 6줄까지입니다.")
			List<@NotBlank(message = "빈 문구 줄은 넣을 수 없습니다.") @Size(max = 30, message = "세로 문구 한 줄은 30자 이하여야 합니다.") String> tagline,

			@NotBlank(message = "이미지 주소를 입력해 주세요.")
			@Size(max = 500, message = "이미지 주소는 500자 이하여야 합니다.")
			String imageUrl,

			@NotBlank(message = "이동 경로를 입력해 주세요.")
			@Size(max = 300, message = "이동 경로는 300자 이하여야 합니다.")
			@Pattern(regexp = ValidationPatterns.INTERNAL_PATH, message = "이동 경로는 /로 시작하는 사이트 안 경로여야 합니다.")
			String destination,

			@NotBlank(message = "버튼 문구를 입력해 주세요.")
			@Size(max = 30, message = "버튼 문구는 30자 이하여야 합니다.")
			String ctaLabel,

			@Valid Link secondaryCta) {

		public HeroSlide {
			eyebrow = orEmpty(eyebrow);
			description = orEmpty(description);
			tagline = tagline == null ? List.of() : tagline;
		}
	}

	/**
	 * @param title       제목 줄(1~3줄)
	 * @param description 설명. 줄바꿈(\n)으로 줄을 나눈다.
	 * @param imageUrl    업로드 API가 돌려준 주소나 웹의 정적 이미지(/assets/...)
	 * @param tone        글자색 기준이 되는 배경 톤(dark, light)
	 * @param destination 누르면 이동할 사이트 안 경로
	 */
	public record PromoBanner(
			@NotBlank(message = "배너 ID를 입력해 주세요.")
			@Pattern(regexp = ITEM_ID, message = "ID는 영문·숫자·-·_ 50자 이내입니다.")
			String id,

			@NotEmpty(message = "제목을 입력해 주세요.")
			@Size(max = 3, message = "제목은 3줄까지입니다.")
			List<@NotBlank(message = "빈 제목 줄은 넣을 수 없습니다.") @Size(max = 50, message = "제목 한 줄은 50자 이하여야 합니다.") String> title,

			@Size(max = 300, message = "설명은 300자 이하여야 합니다.")
			String description,

			@NotBlank(message = "이미지 주소를 입력해 주세요.")
			@Size(max = 500, message = "이미지 주소는 500자 이하여야 합니다.")
			String imageUrl,

			@NotBlank(message = "배경 톤을 골라 주세요.")
			@Pattern(regexp = "^(dark|light)$", message = "배경 톤은 dark 또는 light입니다.")
			String tone,

			@NotBlank(message = "이동 경로를 입력해 주세요.")
			@Size(max = 300, message = "이동 경로는 300자 이하여야 합니다.")
			@Pattern(regexp = ValidationPatterns.INTERNAL_PATH, message = "이동 경로는 /로 시작하는 사이트 안 경로여야 합니다.")
			String destination) {

		public PromoBanner {
			description = orEmpty(description);
		}
	}

	/** 배송·서비스 안내 한 칸(예: 5만 원 이상 무료배송) */
	public record ServiceBenefit(
			@NotBlank(message = "안내 ID를 입력해 주세요.")
			@Pattern(regexp = ITEM_ID, message = "ID는 영문·숫자·-·_ 50자 이내입니다.")
			String id,

			@NotBlank(message = "안내 제목을 입력해 주세요.")
			@Size(max = 50, message = "안내 제목은 50자 이하여야 합니다.")
			String title,

			@Size(max = 200, message = "안내 설명은 200자 이하여야 합니다.")
			String description) {

		public ServiceBenefit {
			description = orEmpty(description);
		}
	}

	/** 홈 하단 견적 문의 띠 */
	public record CtaBand(
			@Size(max = 50, message = "작은 문구는 50자 이하여야 합니다.")
			String eyebrow,

			@NotBlank(message = "견적 문의 띠 제목을 입력해 주세요.")
			@Size(max = 100, message = "견적 문의 띠 제목은 100자 이하여야 합니다.")
			String title,

			@NotNull(message = "견적 문의 띠의 버튼을 입력해 주세요.")
			@Valid Link cta) {

		public CtaBand {
			eyebrow = orEmpty(eyebrow);
		}
	}

	/** 문구와 이동 경로 */
	public record Link(
			@NotBlank(message = "링크 문구를 입력해 주세요.")
			@Size(max = 30, message = "링크 문구는 30자 이하여야 합니다.")
			String label,

			@NotBlank(message = "이동 경로를 입력해 주세요.")
			@Size(max = 300, message = "이동 경로는 300자 이하여야 합니다.")
			@Pattern(regexp = ValidationPatterns.INTERNAL_PATH, message = "이동 경로는 /로 시작하는 사이트 안 경로여야 합니다.")
			String destination) {
	}

	private static String orEmpty(String value) {
		return value == null ? "" : value;
	}
}
