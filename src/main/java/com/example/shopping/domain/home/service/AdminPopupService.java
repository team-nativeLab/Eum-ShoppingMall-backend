package com.example.shopping.domain.home.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.home.dto.request.PopupRequest;
import com.example.shopping.domain.home.dto.response.PopupResponse;
import com.example.shopping.domain.home.entity.Popup;
import com.example.shopping.domain.home.repository.PopupRepository;
import com.example.shopping.domain.product.service.ProductReadCache;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 관리자 팝업 관리(#211). 바꾸면 고객 팝업 목록 캐시를 비운다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPopupService {

	private final PopupRepository popupRepository;
	private final HomeImagePolicy homeImagePolicy;
	private final ProductReadCache readCache;

	/** 숨긴 팝업까지, 정렬 순서대로 */
	public List<PopupResponse.Admin> list() {
		return popupRepository.findAllByOrderBySortOrderAscIdDesc().stream().map(PopupResponse.Admin::from).toList();
	}

	@Transactional
	public PopupResponse.Admin create(PopupRequest request) {
		Popup popup = popupRepository.saveAndFlush(Popup.create(fields(request)));
		readCache.invalidateAfterCommit();
		return PopupResponse.Admin.from(popup);
	}

	@Transactional
	public PopupResponse.Admin update(Long popupId, PopupRequest request) {
		Popup popup = find(popupId);
		popup.change(fields(request));
		popupRepository.saveAndFlush(popup);
		readCache.invalidateAfterCommit();
		return PopupResponse.Admin.from(popup);
	}

	@Transactional
	public void delete(Long popupId) {
		popupRepository.delete(find(popupId));
		readCache.invalidateAfterCommit();
	}

	private Popup find(Long popupId) {
		return popupRepository.findById(popupId).orElseThrow(() -> new BusinessException(ErrorCode.POPUP_NOT_FOUND));
	}

	/** 사진 주소는 앞뒤 공백을 지우고, 비어 있으면 사진 없음(null)이다. */
	private Popup.Fields fields(PopupRequest request) {
		String imageUrl = StringUtils.hasText(request.imageUrl()) ? request.imageUrl().trim() : null;
		if (imageUrl != null) {
			homeImagePolicy.require(imageUrl);
		}
		return new Popup.Fields(request.title().trim(), imageUrl, request.body().trim(),
				request.visible() == null || request.visible(),
				request.sortOrder() == null ? 0 : request.sortOrder());
	}
}
