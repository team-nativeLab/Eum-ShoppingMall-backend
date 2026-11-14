package com.example.shopping.domain.home.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.home.dto.HomeLayout;
import com.example.shopping.domain.home.dto.response.ContentResponse;
import com.example.shopping.domain.home.dto.response.HomeResponse;
import com.example.shopping.domain.home.entity.SiteContent;
import com.example.shopping.domain.home.entity.SiteSetting;
import com.example.shopping.domain.home.repository.BannerRepository;
import com.example.shopping.domain.home.repository.SiteContentRepository;
import com.example.shopping.domain.product.dto.response.ProductSummaryResponse;
import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.domain.product.repository.ProductRepository;
import com.example.shopping.domain.product.service.ProductReadCache;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

	/** 응답 모양이 바뀌어 키를 바꿨다. 배포 중에 이전 버전 서버가 남긴 캐시를 새 모양으로 읽지 않게 한다. */
	private static final String HOME_CACHE_KEY = "home-v2";

	private final BannerRepository bannerRepository;
	private final ProductRepository productRepository;
	private final SiteContentRepository siteContentRepository;
	private final SiteSettingStore siteSettingStore;
	private final ProductReadCache readCache;

	public HomeResponse getHome() {
		return readCache.getOrLoad(HOME_CACHE_KEY, HomeResponse.class, () -> {
			HomeLayout layout = siteSettingStore.find(SiteSetting.HOME_LAYOUT, HomeLayout.class)
					.orElseGet(HomeLayout::empty);
			return new HomeResponse(
					bannerRepository.findByVisibleTrueOrderBySortOrderAscIdAsc().stream()
							.map(HomeResponse.BannerItem::from)
							.toList(),
					productRepository.findTop8ByStatusInOrderByIdDesc(ProductStatus.CUSTOMER_VISIBLE).stream()
							.map(ProductSummaryResponse::from)
							.toList(),
					layout.heroSlides(), layout.promoBanners(), layout.serviceBenefits(),
					featuredProducts(layout.featuredProductIds()), layout.ctaBand());
		});
	}

	public ContentResponse getContent(String code) {
		if (!SiteContent.ALLOWED_CODES.contains(code)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return siteContentRepository.findByCode(code)
				.map(ContentResponse::from)
				.orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
	}

	/** 관리자가 고른 순서대로, 고객에게 보이는 상품만. 숨기거나 삭제한 상품은 빠진다. */
	private List<ProductSummaryResponse> featuredProducts(List<Long> productIds) {
		if (productIds.isEmpty()) {
			return List.of();
		}
		Map<Long, Product> visible = productRepository.findAllById(productIds).stream()
				.filter(product -> ProductStatus.CUSTOMER_VISIBLE.contains(product.getStatus()))
				.collect(Collectors.toMap(Product::getId, Function.identity()));
		return productIds.stream()
				.map(visible::get)
				.filter(Objects::nonNull)
				.map(ProductSummaryResponse::from)
				.toList();
	}
}
