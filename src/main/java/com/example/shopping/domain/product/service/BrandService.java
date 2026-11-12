package com.example.shopping.domain.product.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.product.dto.response.BrandResponse;
import com.example.shopping.domain.product.entity.Brand;
import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.domain.product.repository.BrandRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 고객 화면의 브랜드 목록·상세·대표 브랜드. 숨긴 브랜드는 보이지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandService {

	private final BrandRepository brandRepository;

	/** 보이는 브랜드를 정렬 순서·이름 순으로 */
	public List<BrandResponse.Summary> list() {
		List<Brand> brands = brandRepository.findByVisibleTrueOrderBySortOrderAscNameAsc();
		Map<String, Long> counts = productCounts(brandRepository, brands);
		return brands.stream()
				.map(brand -> BrandResponse.Summary.of(brand, counts.getOrDefault(brand.getName(), 0L)))
				.toList();
	}

	public BrandResponse.Detail get(Long brandId) {
		Brand brand = brandRepository.findById(brandId)
				.filter(Brand::isVisible)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRAND_NOT_FOUND));
		return detail(brand);
	}

	/** 대표 브랜드. 보이는 대표 브랜드 중 정렬 순서가 앞선 하나이고, 없으면 null */
	public BrandResponse.Detail featured() {
		return brandRepository.findFirstByVisibleTrueAndFeaturedTrueOrderBySortOrderAscIdAsc()
				.map(this::detail)
				.orElse(null);
	}

	private BrandResponse.Detail detail(Brand brand) {
		return BrandResponse.Detail.of(brand,
				productCounts(brandRepository, List.of(brand)).getOrDefault(brand.getName(), 0L));
	}

	/** 브랜드 이름별로 고객에게 보이는 상품 수 */
	static Map<String, Long> productCounts(BrandRepository brandRepository, Collection<Brand> brands) {
		Map<String, Long> counts = new HashMap<>();
		if (brands.isEmpty()) {
			return counts;
		}
		List<String> names = brands.stream().map(Brand::getName).toList();
		for (Object[] row : brandRepository.countProductsByBrand(names, ProductStatus.CUSTOMER_VISIBLE)) {
			counts.put((String) row[0], ((Number) row[1]).longValue());
		}
		return counts;
	}
}
