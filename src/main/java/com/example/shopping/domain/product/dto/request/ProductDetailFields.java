package com.example.shopping.domain.product.dto.request;

import java.util.List;

import com.example.shopping.domain.product.entity.ProductDescriptionFormat;

/**
 * 상품 등록·수정 요청에 함께 있는 상세 화면 정보(#198). 수정에서는 null인 항목을 바꾸지 않고, 글은 빈 문자열을 주면 지우며,
 * 갤러리·사양은 보낸 목록으로 통째로 바꾼다. 갤러리를 보내면 첫 이미지가 대표 이미지(imageUrl)가 된다.
 */
public interface ProductDetailFields {

	/** 상품 코드. 다른 상품과 겹치면 409(PRODUCT_CODE_DUPLICATED) */
	String productCode();

	String modelName();

	/** 목록·상세 위쪽의 한 줄 요약 */
	String summary();

	String countryOfOrigin();

	/** 배송 예정 안내(예: 평일 기준 2~3일 이내 출고) */
	String shippingEstimate();

	/** 상품 설명의 서식. 등록할 때 없으면 PLAIN */
	ProductDescriptionFormat descriptionFormat();

	/** 상세 갤러리(10장까지). 업로드 API가 돌려준 주소만 받는다. */
	List<ProductImageRequest> images();

	/** 사양 표(20줄까지) */
	List<ProductSpecificationRequest> specifications();

	/** 상품 코드 형식. 영문·숫자로 시작하고 영문·숫자·-·_ 50자 이내. 수정에서 빈 문자열은 지운다는 뜻이다. */
	String PRODUCT_CODE = "^$|^[0-9A-Za-z][0-9A-Za-z_-]{0,49}$";
}
