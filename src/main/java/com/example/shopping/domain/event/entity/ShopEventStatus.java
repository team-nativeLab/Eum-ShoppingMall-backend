package com.example.shopping.domain.event.entity;

/** 고객에게 보여 주는 이벤트 진행 상태. 저장하지 않고 기간과 현재 시각으로 정한다. */
public enum ShopEventStatus {
	/** 진행 중: 시작 시각 이후, 종료 시각 이전(종료 시각 포함) */
	ONGOING,
	/** 예정: 시작 시각 전 */
	UPCOMING,
	/** 종료: 종료 시각 후 */
	ENDED
}
