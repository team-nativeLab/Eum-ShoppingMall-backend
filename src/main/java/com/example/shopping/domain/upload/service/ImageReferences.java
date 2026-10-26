package com.example.shopping.domain.upload.service;

import java.util.List;

/**
 * 업로드한 이미지의 주소를 저장하는 곳(상품, 배너). 참조 없는 이미지 정리가 쓰는 이미지를 지우지 않도록 알려 준다.
 * 이미지 주소를 저장하는 곳이 늘면 그 리포지토리도 이 인터페이스를 구현해야 한다.
 */
public interface ImageReferences {

	/** 저장된 이미지 주소 전부. 삭제 처리된 상품처럼 고객에게 보이지 않는 데이터의 주소도 포함한다. */
	List<String> findAllImageUrls();
}
