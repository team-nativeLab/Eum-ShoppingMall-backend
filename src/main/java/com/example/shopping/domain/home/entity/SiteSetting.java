package com.example.shopping.domain.home.entity;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 화면 하나의 설정을 통째로 저장하는 JSON 문서. key로 구분하고, 값의 모양은 SiteSettingStore로 읽고 쓰는 record가 정한다.
 * 관리자가 한 번에 저장하고 고객 화면이 통째로 읽는 값(홈 편성, 사업자 정보)이라 표로 나누지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteSetting extends BaseTimeEntity {

	/** 홈 화면 편성(HomeLayout) */
	public static final String HOME_LAYOUT = "home-layout";

	/** 푸터의 사업자·고객센터 정보(CompanyInfo) */
	public static final String COMPANY_INFO = "company-info";

	/** 검색 결과가 없을 때 보여 줄 추천 검색어(RecommendedKeywords) */
	public static final String RECOMMENDED_KEYWORDS = "recommended-keywords";

	@Id
	@Column(name = "setting_key", length = 50)
	private String key;

	@Column(name = "value_json", nullable = false, columnDefinition = "MEDIUMTEXT")
	private String valueJson;

	public static SiteSetting create(String key, String valueJson) {
		SiteSetting setting = new SiteSetting();
		setting.key = key;
		setting.valueJson = valueJson;
		return setting;
	}

	public void change(String valueJson) {
		this.valueJson = valueJson;
	}
}
