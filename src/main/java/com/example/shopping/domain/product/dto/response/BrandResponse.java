package com.example.shopping.domain.product.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.product.entity.Brand;

/** 브랜드 응답. 고객 목록(Summary), 고객 상세(Detail), 관리자(Admin)로 나눈다. */
public final class BrandResponse {

	private BrandResponse() {
	}

	/**
	 * 브랜드 목록의 한 칸
	 *
	 * @param productCount 고객에게 보이는 이 브랜드 상품 수. 상품 목록은 GET /api/v1/products?brand={name}으로 본다.
	 */
	public record Summary(Long id, String name, String englishName, String logoUrl, String tagline,
			long productCount) {

		public static Summary of(Brand brand, long productCount) {
			return new Summary(brand.getId(), brand.getName(), brand.getEnglishName(), brand.getLogoUrl(),
					brand.getTagline(), productCount);
		}
	}

	/** @param story 브랜드 스토리. 제목과 본문이 모두 없으면 null */
	public record Detail(Long id, String name, String englishName, String logoUrl, String tagline,
			long productCount, String coverImageUrl, String mainField, String originCountry, String certification,
			Story story) {

		public static Detail of(Brand brand, long productCount) {
			return new Detail(brand.getId(), brand.getName(), brand.getEnglishName(), brand.getLogoUrl(),
					brand.getTagline(), productCount, brand.getCoverImageUrl(), brand.getMainField(),
					brand.getOriginCountry(), brand.getCertification(), Story.of(brand));
		}
	}

	public record Story(String title, String body) {

		static Story of(Brand brand) {
			return brand.getStoryTitle() == null && brand.getStoryBody() == null ? null
					: new Story(brand.getStoryTitle(), brand.getStoryBody());
		}
	}

	public record Admin(Long id, String name, String englishName, String logoUrl, String tagline,
			String coverImageUrl, String mainField, String originCountry, String certification, String storyTitle,
			String storyBody, boolean visible, boolean featured, int sortOrder, long productCount,
			LocalDateTime createdAt, LocalDateTime updatedAt) {

		public static Admin of(Brand brand, long productCount) {
			return new Admin(brand.getId(), brand.getName(), brand.getEnglishName(), brand.getLogoUrl(),
					brand.getTagline(), brand.getCoverImageUrl(), brand.getMainField(), brand.getOriginCountry(),
					brand.getCertification(), brand.getStoryTitle(), brand.getStoryBody(), brand.isVisible(),
					brand.isFeatured(), brand.getSortOrder(), productCount, brand.getCreatedAt(),
					brand.getUpdatedAt());
		}
	}
}
