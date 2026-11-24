package com.example.shopping.domain.home.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.home.dto.response.PopupResponse;
import com.example.shopping.domain.home.repository.PopupRepository;
import com.example.shopping.domain.product.service.ProductReadCache;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;

/** 웹 홈 팝업(#211). 홈마다 부르므로 짧게 캐시하고, 관리자가 바꾸면 비운다(AdminPopupService). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PopupService {

	private static final String CACHE_KEY = "popups-v1";
	private static final TypeReference<List<PopupResponse>> POPUP_LIST = new TypeReference<>() {};

	private final PopupRepository popupRepository;
	private final ProductReadCache readCache;

	/** 노출 중인 팝업만, 정렬 순서대로. 같으면 최근 등록한 팝업이 앞 */
	public List<PopupResponse> visible() {
		return readCache.getOrLoad(CACHE_KEY, POPUP_LIST, () -> popupRepository
				.findByVisibleTrueOrderBySortOrderAscIdDesc().stream()
				.map(PopupResponse::from)
				.toList());
	}
}
