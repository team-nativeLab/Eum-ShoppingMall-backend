package com.example.shopping.domain.home.dto.response;

import com.example.shopping.domain.home.entity.Banner;

/**
 * 관리자 배너. 숨긴 배너도 들어 있다.
 *
 * @param id        배너 ID
 * @param imageUrl  배너 이미지 주소
 * @param linkUrl   배너를 눌렀을 때 이동할 주소. 없으면 null
 * @param sortOrder 정렬 순서. 작을수록 앞
 * @param visible   고객 화면에 보이는지
 */
public record BannerResponse(Long id, String imageUrl, String linkUrl, int sortOrder, boolean visible) {

	public static BannerResponse from(Banner banner) {
		return new BannerResponse(banner.getId(), banner.getImageUrl(), banner.getLinkUrl(), banner.getSortOrder(),
				banner.isVisible());
	}
}
