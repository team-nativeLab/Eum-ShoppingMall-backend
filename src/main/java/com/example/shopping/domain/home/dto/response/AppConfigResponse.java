package com.example.shopping.domain.home.dto.response;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 앱이 스플래시에서 받아 가는 구동 설정. 값은 설정(app.client)에서 읽는다.
 *
 * @param minVersion         이보다 낮은 버전의 앱은 업데이트를 강제한다.
 * @param latestVersion      최신 버전 (업데이트 권유용)
 * @param maintenance        점검 중이면 true
 * @param maintenanceMessage 점검 중일 때 보여줄 안내 문구
 */
@ConfigurationProperties("app.client")
public record AppConfigResponse(
		@DefaultValue("1.0.0") String minVersion,
		@DefaultValue("1.0.0") String latestVersion,
		@DefaultValue("false") boolean maintenance,
		@DefaultValue("") String maintenanceMessage) {
}
