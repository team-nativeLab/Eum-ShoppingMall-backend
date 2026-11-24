package com.example.shopping.domain.product.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.product.entity.Brand;
import com.example.shopping.domain.product.entity.BrandImageSettings;

/** 브랜드 응답. 고객 목록(Summary), 고객 상세(Detail), 관리자(Admin)로 나눈다. */
public final class BrandResponse {

	private BrandResponse() {
	}

	/**
	 * 브랜드 목록의 한 칸
	 *
	 * @param productCount 고객에게 보이는 이 브랜드 상품 수. 상품 목록은 GET /api/v1/products?brand={name}으로 본다.
	 * @param logoSettings 로고 표시 방식(#214)
	 * @param accentColor  대표 색상(#RRGGBB, #214)
	 */
	public record Summary(Long id, String name, String englishName, String logoUrl, String tagline,
			long productCount, BrandImageSettings logoSettings, String accentColor) {

		public static Summary of(Brand brand, long productCount) {
			return new Summary(brand.getId(), brand.getName(), brand.getEnglishName(), brand.getLogoUrl(),
					brand.getTagline(), productCount, brand.getLogoSettings(), brand.getAccentColor());
		}
	}

	/**
	 * @param story         브랜드 스토리. 제목과 본문이 모두 없으면 null
	 * @param logoSettings  로고 표시 방식(#214)
	 * @param accentColor   대표 색상(#RRGGBB, #214)
	 * @param coverSettings 커버 표시 방식(#214)
	 */
	public record Detail(Long id, String name, String englishName, String logoUrl, String tagline,
			long productCount, String coverImageUrl, String mainField, String originCountry, String certification,
			Story story, BrandImageSettings logoSettings, String accentColor, BrandImageSettings coverSettings) {

		public static Detail of(Brand brand, long productCount) {
			return new Detail(brand.getId(), brand.getName(), brand.getEnglishName(), brand.getLogoUrl(),
					brand.getTagline(), productCount, brand.getCoverImageUrl(), brand.getMainField(),
					brand.getOriginCountry(), brand.getCertification(), Story.of(brand), brand.getLogoSettings(),
					brand.getAccentColor(), brand.getCoverSettings());
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
			LocalDateTime createdAt, LocalDateTime updatedAt, String accentColor, BrandImageSettings logoSettings,
			BrandImageSettings coverSettings) {

		public static Admin of(Brand brand, long productCount) {
			return new Admin(brand.getId(), brand.getName(), brand.getEnglishName(), brand.getLogoUrl(),
					brand.getTagline(), brand.getCoverImageUrl(), brand.getMainField(), brand.getOriginCountry(),
					brand.getCertification(), brand.getStoryTitle(), brand.getStoryBody(), brand.isVisible(),
					brand.isFeatured(), brand.getSortOrder(), productCount, brand.getCreatedAt(),
					brand.getUpdatedAt(), brand.getAccentColor(), brand.getLogoSettings(), brand.getCoverSettings());
		}
	}
}
