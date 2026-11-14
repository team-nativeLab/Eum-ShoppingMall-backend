package com.example.shopping.domain.home.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.home.dto.RecommendedKeywords;
import com.example.shopping.domain.home.entity.SiteSetting;

import lombok.RequiredArgsConstructor;

/** 검색 결과가 없을 때 보여 줄 추천 검색어. 운영자가 관리한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendedKeywordService {

	private final SiteSettingStore siteSettingStore;

	/** 저장한 적이 없으면 빈 목록 */
	public RecommendedKeywords get() {
		return siteSettingStore.find(SiteSetting.RECOMMENDED_KEYWORDS, RecommendedKeywords.class)
				.orElseGet(RecommendedKeywords::empty);
	}

	public List<String> getKeywords() {
		return get().keywords();
	}

	@Transactional
	public RecommendedKeywords save(RecommendedKeywords keywords) {
		siteSettingStore.save(SiteSetting.RECOMMENDED_KEYWORDS, keywords);
		return keywords;
	}
}
