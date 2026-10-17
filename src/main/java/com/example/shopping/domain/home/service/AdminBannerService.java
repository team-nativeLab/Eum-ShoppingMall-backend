package com.example.shopping.domain.home.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.home.dto.BannerCreateRequest;
import com.example.shopping.domain.home.dto.BannerResponse;
import com.example.shopping.domain.home.dto.BannerUpdateRequest;
import com.example.shopping.domain.home.entity.Banner;
import com.example.shopping.domain.home.repository.BannerRepository;
import com.example.shopping.domain.product.service.ProductReadCache;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBannerService {

	private final BannerRepository bannerRepository;
	private final ProductReadCache readCache;

	/** 비노출 배너까지 전부, 노출 순서대로 */
	public List<BannerResponse> getAll() {
		return bannerRepository.findAllByOrderBySortOrderAscIdAsc().stream()
				.map(BannerResponse::from)
				.toList();
	}

	@Transactional
	public BannerResponse create(BannerCreateRequest request) {
		int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
		boolean visible = request.visible() == null || request.visible();
		Banner banner = Banner.create(request.imageUrl(), request.linkUrl(), sortOrder, visible);
		BannerResponse response = BannerResponse.from(bannerRepository.save(banner));
		readCache.invalidateAfterCommit();
		return response;
	}

	@Transactional
	public BannerResponse update(Long bannerId, BannerUpdateRequest request) {
		Banner banner = bannerRepository.findById(bannerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BANNER_NOT_FOUND));
		banner.update(request.imageUrl(), request.linkUrl(), request.sortOrder(), request.visible());
		readCache.invalidateAfterCommit();
		return BannerResponse.from(banner);
	}

	@Transactional
	public void delete(Long bannerId) {
		if (!bannerRepository.existsById(bannerId)) {
			throw new BusinessException(ErrorCode.BANNER_NOT_FOUND);
		}
		bannerRepository.deleteById(bannerId);
		readCache.invalidateAfterCommit();
	}
}
