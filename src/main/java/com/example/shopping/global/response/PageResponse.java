package com.example.shopping.global.response;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * 페이지로 나눈 목록. 요청의 page(0부터)·size·sort를 따른다.
 *
 * @param content       이 페이지의 항목
 * @param page          페이지 번호(0부터)
 * @param size          페이지 크기
 * @param totalElements 전체 항목 수
 * @param totalPages    전체 페이지 수
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}
}
