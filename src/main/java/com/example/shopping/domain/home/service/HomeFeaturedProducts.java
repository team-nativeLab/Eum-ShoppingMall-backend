package com.example.shopping.domain.home.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.shopping.domain.home.dto.HomeLayout;
import com.example.shopping.domain.home.entity.SiteSetting;
import com.example.shopping.domain.product.service.FeaturedProductListener;
import com.example.shopping.domain.product.service.ProductReadCache;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 상품 편집에서 바꾼 베스트 지정을 홈 편성의 베스트 목록에 반영한다(#219). 반대 방향(홈 편성 저장 → 상품의 베스트 지정)은
 * AdminHomeLayoutService가 맞춘다. 관리자 화면은 두 값을 하나의 "베스트"로 다룬다.
 */
@Component
@RequiredArgsConstructor
public class HomeFeaturedProducts implements FeaturedProductListener {

	private final SiteSettingStore siteSettingStore;
	private final ProductReadCache readCache;

	/** 이미 목록에 있거나 없으면 그대로 둔다. 목록이 꽉 찼는데 넣으려 하면 409(HOME_FEATURED_PRODUCTS_FULL) */
	@Override
	public void featuredChanged(Long productId, boolean featured) {
		HomeLayout layout = siteSettingStore.find(SiteSetting.HOME_LAYOUT, HomeLayout.class)
				.orElseGet(HomeLayout::empty);
		List<Long> productIds = new ArrayList<>(layout.featuredProductIds());
		if (productIds.contains(productId) == featured) {
			return;
		}
		if (featured) {
			if (productIds.size() >= HomeLayout.MAX_FEATURED_PRODUCTS) {
				throw new BusinessException(ErrorCode.HOME_FEATURED_PRODUCTS_FULL);
			}
			productIds.add(productId);
		} else {
			productIds.remove(productId);
		}
		siteSettingStore.save(SiteSetting.HOME_LAYOUT, layout.withFeaturedProductIds(productIds));
		readCache.invalidateAfterCommit();
	}

	@Override
	public void productDeleted(Long productId) {
		featuredChanged(productId, false);
	}
}
