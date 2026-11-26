package com.example.shopping.domain.upload.dto.response;

/**
 * url을 상품·배너 등록 요청의 imageUrl에 그대로 넣는다.
 *
 * @param url 올린 이미지 주소
 */
public record UploadResponse(String url) {
}
