package com.example.shopping.domain.home.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.home.dto.ContentResponse;
import com.example.shopping.domain.home.dto.HomeResponse;
import com.example.shopping.domain.home.entity.SiteContent;
import com.example.shopping.domain.home.repository.BannerRepository;
import com.example.shopping.domain.home.repository.SiteContentRepository;
import com.example.shopping.domain.product.dto.ProductSummaryResponse;
import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.domain.product.repository.ProductRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

	private final BannerRepository bannerRepository;
	private final ProductRepository productRepository;
	private final SiteContentRepository siteContentRepository;

	public HomeResponse getHome() {
		return new HomeResponse(
				bannerRepository.findByVisibleTrueOrderBySortOrderAscIdAsc().stream()
						.map(HomeResponse.BannerItem::from)
						.toList(),
				productRepository.findTop8ByStatusInOrderByIdDesc(ProductStatus.CUSTOMER_VISIBLE).stream()
						.map(ProductSummaryResponse::from)
						.toList());
	}

	public ContentResponse getCompany() {
		return siteContentRepository.findByCode(SiteContent.COMPANY)
				.map(ContentResponse::from)
				.orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
	}
}
