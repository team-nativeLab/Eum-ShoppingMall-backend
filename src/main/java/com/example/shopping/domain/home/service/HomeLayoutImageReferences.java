package com.example.shopping.domain.home.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.shopping.domain.home.dto.HomeLayout;
import com.example.shopping.domain.home.entity.SiteSetting;
import com.example.shopping.domain.upload.service.ImageReferences;

import lombok.RequiredArgsConstructor;

/** 홈 편성(히어로·프로모션)이 쓰는 이미지. 참조 없는 이미지 정리(UnusedImageCleaner)가 지우지 않게 알린다. */
@Component
@RequiredArgsConstructor
public class HomeLayoutImageReferences implements ImageReferences {

	private final SiteSettingStore siteSettingStore;

	@Override
	public List<String> findAllImageUrls() {
		return siteSettingStore.find(SiteSetting.HOME_LAYOUT, HomeLayout.class)
				.map(HomeLayout::imageUrls)
				.orElse(List.of());
	}
}
