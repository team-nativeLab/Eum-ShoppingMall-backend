package com.example.shopping.domain.home.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.shopping.domain.home.entity.SiteSetting;
import com.example.shopping.domain.home.repository.SiteSettingRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/** site_setting의 JSON 문서를 record로 읽고 쓴다. 트랜잭션은 부르는 서비스가 정한다. */
@Component
@RequiredArgsConstructor
public class SiteSettingStore {

	private final SiteSettingRepository siteSettingRepository;
	private final JsonMapper jsonMapper;

	/** 저장한 적이 없으면 빈 값 */
	public <T> Optional<T> find(String key, Class<T> type) {
		return siteSettingRepository.findById(key)
				.map(setting -> jsonMapper.readValue(setting.getValueJson(), type));
	}

	/** 통째로 바꾼다. 처음이면 새로 만든다. */
	public void save(String key, Object value) {
		String json = jsonMapper.writeValueAsString(value);
		siteSettingRepository.findById(key).ifPresentOrElse(setting -> setting.change(json),
				() -> siteSettingRepository.save(SiteSetting.create(key, json)));
	}
}
