package com.example.shopping.domain.home.dto.response;

import java.util.List;

import com.example.shopping.domain.home.dto.HomeLayout;
import com.example.shopping.domain.home.entity.Banner;
import com.example.shopping.domain.product.dto.response.ProductSummaryResponse;

/**
 * 고객 홈. 홈 편성(heroSlides~ctaBand)은 관리자가 저장한 그대로이고, 저장한 적이 없으면 빈 목록과 null이다.
 *
 * @param banners          노출 중인 배너 (노출 순서대로)
 * @param newProducts      최근 등록된 상품 8개
 * @param heroSlides       히어로 슬라이드
 * @param promoBanners     프로모션 배너
 * @param serviceBenefits  배송·서비스 안내
 * @param featuredProducts 베스트 상품. 관리자가 고른 순서대로, 고객에게 보이는 상품만
 * @param ctaBand          하단 견적 문의 띠. 운영에서 끄면 null
 */
public record HomeResponse(List<BannerItem> banners, List<ProductSummaryResponse> newProducts,
		List<HomeLayout.HeroSlide> heroSlides, List<HomeLayout.PromoBanner> promoBanners,
		List<HomeLayout.ServiceBenefit> serviceBenefits, List<ProductSummaryResponse> featuredProducts,
		HomeLayout.CtaBand ctaBand) {

	public record BannerItem(Long id, String imageUrl, String linkUrl) {

		public static BannerItem from(Banner banner) {
			return new BannerItem(banner.getId(), banner.getImageUrl(), banner.getLinkUrl());
		}
	}
}
