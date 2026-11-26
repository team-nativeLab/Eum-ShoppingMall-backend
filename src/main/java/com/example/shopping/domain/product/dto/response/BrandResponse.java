package com.example.shopping.domain.product.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.product.entity.Brand;
import com.example.shopping.domain.product.entity.BrandImageSettings;

import io.swagger.v3.oas.annotations.media.Schema;

/** 브랜드 응답. 고객 목록(Summary), 고객 상세(Detail), 관리자(Admin)로 나눈다. */
public final class BrandResponse {

	private BrandResponse() {
	}

	/**
	 * 브랜드 목록의 한 칸
	 *
	 * @param id           브랜드 ID
	 * @param name         브랜드 이름(상품의 brand 값)
	 * @param englishName  영문 이름. 없으면 null
	 * @param logoUrl      로고 주소. 없으면 null
	 * @param tagline      한 줄 소개. 없으면 null
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
	 * 브랜드 상세
	 *
	 * @param id            브랜드 ID
	 * @param name          브랜드 이름(상품의 brand 값)
	 * @param englishName   영문 이름. 없으면 null
	 * @param logoUrl       로고 주소. 없으면 null
	 * @param tagline       한 줄 소개. 없으면 null
	 * @param productCount  고객에게 보이는 이 브랜드 상품 수
	 * @param coverImageUrl 커버 이미지 주소. 없으면 null
	 * @param mainField     주력 분야. 없으면 null
	 * @param originCountry 원산지. 없으면 null
	 * @param certification 인증 문구. 없으면 null
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

	/**
	 * 브랜드 스토리
	 *
	 * @param title 제목. 없으면 null
	 * @param body  본문. 없으면 null
	 */
	public record Story(String title, String body) {

		static Story of(Brand brand) {
			return brand.getStoryTitle() == null && brand.getStoryBody() == null ? null
					: new Story(brand.getStoryTitle(), brand.getStoryBody());
		}
	}

	/**
	 * 관리자 브랜드. 숨긴 브랜드도 들어 있다.
	 *
	 * @param id            브랜드 ID
	 * @param name          브랜드 이름(상품의 brand 값)
	 * @param englishName   영문 이름. 없으면 null
	 * @param logoUrl       로고 주소. 없으면 null
	 * @param tagline       한 줄 소개. 없으면 null
	 * @param coverImageUrl 커버 이미지 주소. 없으면 null
	 * @param mainField     주력 분야. 없으면 null
	 * @param originCountry 원산지. 없으면 null
	 * @param certification 인증 문구. 없으면 null
	 * @param storyTitle    브랜드 스토리 제목. 없으면 null
	 * @param storyBody     브랜드 스토리 본문. 없으면 null
	 * @param visible       고객 화면에 보이는지
	 * @param featured      브랜드 목록 위의 대표 브랜드로 쓰는지
	 * @param sortOrder     정렬 순서. 작을수록 앞
	 * @param productCount  고객에게 보이는 이 브랜드 상품 수
	 * @param createdAt     등록 시각
	 * @param updatedAt     마지막 수정 시각
	 * @param accentColor   대표 색상(#RRGGBB)
	 * @param logoSettings  로고 표시 방식
	 * @param coverSettings 커버 표시 방식
	 */
	@Schema(name = "BrandAdminResponse")
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
