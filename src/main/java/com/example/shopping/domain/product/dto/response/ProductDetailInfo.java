package com.example.shopping.domain.product.dto.response;

import java.util.List;

import com.example.shopping.domain.product.entity.Product;

/** 상품 상세 응답(고객·관리자)이 함께 쓰는 갤러리 이미지·사양 표 행 */
public final class ProductDetailInfo {

	private ProductDetailInfo() {
	}

	/** @param alt 대체 문구. 없으면 빈 문자열 */
	public record Image(String url, String alt) {
	}

	public record Specification(String label, String value) {
	}

	static List<Image> images(Product product) {
		return product.getImages().stream().map(image -> new Image(image.getUrl(), image.getAlt())).toList();
	}

	static List<Specification> specifications(Product product) {
		return product.getSpecifications().stream()
				.map(specification -> new Specification(specification.getLabel(), specification.getValue()))
				.toList();
	}
}
