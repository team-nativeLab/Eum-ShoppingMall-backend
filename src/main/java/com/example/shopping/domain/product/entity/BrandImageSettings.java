package com.example.shopping.domain.product.entity;

/**
 * 브랜드 로고·커버 이미지를 그리는 방식(#214). 웹 브랜드 목록·선택 패널·상세가 이 값으로 영역을 잡는다.
 *
 * @param fit    contain(전체 표시) 또는 cover(영역 채우기)
 * @param width  영역 너비(px)
 * @param height 영역 높이(px)
 */
public record BrandImageSettings(String fit, int width, int height) {

	/** fit에 쓸 수 있는 값 */
	public static final String FIT_PATTERN = "^(contain|cover)$";

	/** 따로 정하지 않은 로고. 이전에 등록한 브랜드도 이 값이다. */
	public static final BrandImageSettings DEFAULT_LOGO = new BrandImageSettings("contain", 240, 96);

	/** 따로 정하지 않은 커버. 이전에 등록한 브랜드도 이 값이다. */
	public static final BrandImageSettings DEFAULT_COVER = new BrandImageSettings("cover", 1200, 704);
}
