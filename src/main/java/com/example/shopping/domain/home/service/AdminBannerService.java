package com.example.shopping.domain.home.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.home.dto.request.BannerCreateRequest;
import com.example.shopping.domain.home.dto.response.BannerResponse;
import com.example.shopping.domain.home.dto.request.BannerUpdateRequest;
import com.example.shopping.domain.home.entity.Banner;
import com.example.shopping.domain.home.repository.BannerRepository;
import com.example.shopping.domain.product.service.ProductReadCache;
import com.example.shopping.domain.upload.service.UploadService;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBannerService {

	private final BannerRepository bannerRepository;
	private final ProductReadCache readCache;
	private final UploadService uploadService;

	/** 비노출 배너까지 전부, 노출 순서대로 */
	public List<BannerResponse> getAll() {
		return bannerRepository.findAllByOrderBySortOrderAscIdAsc().stream()
				.map(BannerResponse::from)
				.toList();
	}

	@Transactional
	public BannerResponse create(BannerCreateRequest request) {
		uploadService.requireUploadedImageUrl(request.imageUrl());
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
		// 수정 화면이 기존 값을 그대로 다시 보내도 통과시킨다. 제한 전에 저장한 외부 주소일 수 있다.
		if (!Objects.equals(request.imageUrl(), banner.getImageUrl())) {
			uploadService.requireUploadedImageUrl(request.imageUrl());
		}
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
