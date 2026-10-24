package com.example.shopping.domain.home.dto.response;

import com.example.shopping.domain.home.entity.Banner;

public record BannerResponse(Long id, String imageUrl, String linkUrl, int sortOrder, boolean visible) {

	public static BannerResponse from(Banner banner) {
		return new BannerResponse(banner.getId(), banner.getImageUrl(), banner.getLinkUrl(), banner.getSortOrder(),
				banner.isVisible());
	}
}
