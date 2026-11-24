package com.example.shopping.domain.home.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.home.entity.Popup;

/**
 * 웹 홈 팝업(#211). 관리자가 입력한 글이라 화면에서 텍스트로만 그린다.
 *
 * @param imageUrl 사진이 없으면 null
 */
public record PopupResponse(Long id, String title, String imageUrl, String body) {

	public static PopupResponse from(Popup popup) {
		return new PopupResponse(popup.getId(), popup.getTitle(), popup.getImageUrl(), popup.getBody());
	}

	/** 관리자 팝업. 숨긴 팝업도 들어 있다. */
	public record Admin(Long id, String title, String imageUrl, String body, boolean visible, int sortOrder,
			LocalDateTime updatedAt) {

		public static Admin from(Popup popup) {
			return new Admin(popup.getId(), popup.getTitle(), popup.getImageUrl(), popup.getBody(),
					popup.isVisible(), popup.getSortOrder(), popup.getUpdatedAt());
		}
	}
}
