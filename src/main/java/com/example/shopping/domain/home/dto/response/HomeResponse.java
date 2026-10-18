package com.example.shopping.domain.home.dto.response;

import java.util.List;

import com.example.shopping.domain.home.entity.Banner;
import com.example.shopping.domain.product.dto.response.ProductSummaryResponse;

/**
 * @param banners     노출 중인 배너 (노출 순서대로)
 * @param newProducts 최근 등록된 상품 8개
 */
public record HomeResponse(List<BannerItem> banners, List<ProductSummaryResponse> newProducts) {

	public record BannerItem(Long id, String imageUrl, String linkUrl) {

		public static BannerItem from(Banner banner) {
			return new BannerItem(banner.getId(), banner.getImageUrl(), banner.getLinkUrl());
		}
	}
}
