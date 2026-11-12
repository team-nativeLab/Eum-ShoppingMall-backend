package com.example.shopping.domain.product.service;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.product.dto.request.BrandRequest;
import com.example.shopping.domain.product.dto.response.BrandResponse;
import com.example.shopping.domain.product.entity.Brand;
import com.example.shopping.domain.product.repository.BrandRepository;
import com.example.shopping.domain.product.repository.ProductRepository;
import com.example.shopping.domain.upload.service.UploadService;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 관리자 브랜드 관리. 브랜드 이름을 바꾸면 그 이름을 쓰던 상품의 브랜드도 함께 바꾼다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBrandService {

	private final BrandRepository brandRepository;
	private final ProductRepository productRepository;
	private final UploadService uploadService;

	/** 숨긴 브랜드를 포함한 전체. 정렬 순서·이름 순 */
	public List<BrandResponse.Admin> list() {
		List<Brand> brands = brandRepository.findAllByOrderBySortOrderAscNameAsc();
		Map<String, Long> counts = BrandService.productCounts(brandRepository, brands);
		return brands.stream()
				.map(brand -> BrandResponse.Admin.of(brand, counts.getOrDefault(brand.getName(), 0L)))
				.toList();
	}

	/** 같은 이름의 브랜드가 있으면 409 */
	@Transactional
	public BrandResponse.Admin create(BrandRequest request) {
		Brand.Fields fields = fields(request);
		if (brandRepository.existsByName(fields.name())) {
			throw new BusinessException(ErrorCode.BRAND_NAME_DUPLICATED);
		}
		try {
			return admin(brandRepository.saveAndFlush(Brand.create(fields)));
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.BRAND_NAME_DUPLICATED);
		}
	}

	/** 이름을 바꾸면 이전 이름을 쓰던 상품의 브랜드도 새 이름으로 바꿔, 브랜드 상세의 상품 목록이 끊기지 않게 한다. */
	@Transactional
	public BrandResponse.Admin update(Long brandId, BrandRequest request) {
		Brand brand = find(brandId);
		Brand.Fields fields = fields(request);
		if (brandRepository.existsByNameAndIdNot(fields.name(), brandId)) {
			throw new BusinessException(ErrorCode.BRAND_NAME_DUPLICATED);
		}
		String previousName = brand.getName();
		brand.change(fields);
		brandRepository.flush();
		if (!previousName.equals(fields.name())) {
			productRepository.renameBrand(previousName, fields.name());
		}
		return admin(brand);
	}

	/** 브랜드 소개만 지운다. 상품의 브랜드 값은 그대로다. */
	@Transactional
	public void delete(Long brandId) {
		brandRepository.delete(find(brandId));
	}

	private Brand find(Long brandId) {
		return brandRepository.findById(brandId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRAND_NOT_FOUND));
	}

	private BrandResponse.Admin admin(Brand brand) {
		return BrandResponse.Admin.of(brand,
				BrandService.productCounts(brandRepository, List.of(brand)).getOrDefault(brand.getName(), 0L));
	}

	private Brand.Fields fields(BrandRequest request) {
		String logoUrl = blankToNull(request.logoUrl());
		String coverImageUrl = blankToNull(request.coverImageUrl());
		uploadService.requireUploadedImageUrl(logoUrl);
		uploadService.requireUploadedImageUrl(coverImageUrl);
		return new Brand.Fields(request.name().trim(), blankToNull(request.englishName()), logoUrl,
				blankToNull(request.tagline()), coverImageUrl, blankToNull(request.mainField()),
				blankToNull(request.originCountry()), blankToNull(request.certification()),
				blankToNull(request.storyTitle()), blankToNull(request.storyBody()),
				request.visible() == null || request.visible(), Boolean.TRUE.equals(request.featured()),
				request.sortOrder() == null ? 0 : request.sortOrder());
	}

	private static String blankToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
}
