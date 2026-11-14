package com.example.shopping.domain.home.service;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.home.dto.HomeLayout;
import com.example.shopping.domain.home.entity.SiteSetting;
import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.domain.product.repository.ProductRepository;
import com.example.shopping.domain.product.service.ProductReadCache;
import com.example.shopping.domain.upload.service.UploadService;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 관리자 홈 화면 편성. 화면 단위로 통째로 저장하고, 저장하면 고객 홈 캐시를 비운다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminHomeLayoutService {

	/**
	 * 웹(Next.js)이 함께 배포하는 정적 이미지(예: /assets/web/hero-v3.webp). 사이트 안 경로라 다른 사이트의 이미지를 끌어오지 않는다.
	 * 경로를 거슬러 올라가지 못하게 영문·숫자·-·_ 이름과 이미지 확장자만 받는다.
	 */
	private static final Pattern STATIC_ASSET_IMAGE = Pattern
			.compile("^/assets/[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*\\.(webp|png|jpe?g|gif|svg|avif)$");

	private final SiteSettingStore siteSettingStore;
	private final ProductRepository productRepository;
	private final UploadService uploadService;
	private final ProductReadCache readCache;

	/** 저장한 적이 없으면 빈 편성 */
	public HomeLayout get() {
		return siteSettingStore.find(SiteSetting.HOME_LAYOUT, HomeLayout.class).orElseGet(HomeLayout::empty);
	}

	/**
	 * 이미지 주소·항목 ID·베스트 상품을 확인하고 통째로 바꾼다. 베스트 상품은 겹치면 처음 것만 남긴다.
	 * 숨긴 상품도 저장은 되며, 고객 홈에는 고객에게 보이는 상품만 나간다.
	 */
	@Transactional
	public HomeLayout save(HomeLayout layout) {
		layout.itemIdGroups().forEach(AdminHomeLayoutService::requireUniqueIds);
		layout.imageUrls().forEach(this::requireHomeImage);
		List<Long> productIds = List.copyOf(new LinkedHashSet<>(layout.featuredProductIds()));
		requireProducts(productIds);
		HomeLayout saved = layout.withFeaturedProductIds(productIds);
		siteSettingStore.save(SiteSetting.HOME_LAYOUT, saved);
		readCache.invalidateAfterCommit();
		return saved;
	}

	private void requireHomeImage(String url) {
		if (!STATIC_ASSET_IMAGE.matcher(url).matches() && !uploadService.isUploadedImageUrl(url)) {
			throw new BusinessException(ErrorCode.INVALID_HOME_IMAGE_URL);
		}
	}

	private static void requireUniqueIds(List<String> ids) {
		Set<String> seen = new HashSet<>();
		for (String id : ids) {
			if (!seen.add(id)) {
				throw new BusinessException(ErrorCode.HOME_ITEM_ID_DUPLICATED);
			}
		}
	}

	/** 없거나 삭제한 상품은 404 */
	private void requireProducts(List<Long> productIds) {
		long found = productRepository.findAllById(productIds).stream()
				.map(Product::getStatus)
				.filter(status -> status != ProductStatus.DELETED)
				.count();
		if (found != productIds.size()) {
			throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
		}
	}
}
