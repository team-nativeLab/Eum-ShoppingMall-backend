package com.example.shopping.domain.home.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.home.entity.Popup;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 웹 홈 팝업(#211). 관리자가 입력한 글이라 화면에서 텍스트로만 그린다.
 *
 * @param id       팝업 ID
 * @param title    팝업 제목
 * @param imageUrl 사진이 없으면 null
 * @param body     팝업 본문
 */
public record PopupResponse(Long id, String title, String imageUrl, String body) {

	public static PopupResponse from(Popup popup) {
		return new PopupResponse(popup.getId(), popup.getTitle(), popup.getImageUrl(), popup.getBody());
	}

	/**
	 * 관리자 팝업. 숨긴 팝업도 들어 있다.
	 *
	 * @param id        팝업 ID
	 * @param title     팝업 제목
	 * @param imageUrl  사진 주소. 없으면 null
	 * @param body      팝업 본문
	 * @param visible   고객 화면에 보이는지
	 * @param sortOrder 정렬 순서. 작을수록 앞
	 * @param updatedAt 마지막 수정 시각
	 */
	@Schema(name = "PopupAdminResponse")
	public record Admin(Long id, String title, String imageUrl, String body, boolean visible, int sortOrder,
			LocalDateTime updatedAt) {

		public static Admin from(Popup popup) {
			return new Admin(popup.getId(), popup.getTitle(), popup.getImageUrl(), popup.getBody(),
					popup.isVisible(), popup.getSortOrder(), popup.getUpdatedAt());
		}
	}
}
