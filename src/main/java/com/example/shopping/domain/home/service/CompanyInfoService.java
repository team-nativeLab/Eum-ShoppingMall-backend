package com.example.shopping.domain.home.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.home.dto.CompanyInfo;
import com.example.shopping.domain.home.entity.SiteSetting;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 푸터의 사업자·고객센터 정보 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyInfoService {

	private final SiteSettingStore siteSettingStore;

	/** 고객 화면용. 관리자가 아직 저장하지 않았으면 404 */
	public CompanyInfo get() {
		return siteSettingStore.find(SiteSetting.COMPANY_INFO, CompanyInfo.class)
				.orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
	}

	/** 관리자 편집 화면용. 아직 저장하지 않았으면 null(빈 양식) */
	public CompanyInfo getForAdmin() {
		return siteSettingStore.find(SiteSetting.COMPANY_INFO, CompanyInfo.class).orElse(null);
	}

	@Transactional
	public CompanyInfo save(CompanyInfo companyInfo) {
		siteSettingStore.save(SiteSetting.COMPANY_INFO, companyInfo);
		return companyInfo;
	}
}
