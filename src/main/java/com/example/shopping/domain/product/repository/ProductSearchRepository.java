package com.example.shopping.domain.product.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.shopping.domain.product.entity.Product;

/** 조건과 정렬이 요청마다 달라지는 고객용 상품 검색. 구현은 ProductSearchRepositoryImpl */
public interface ProductSearchRepository {

	/** @param pageable 페이지 번호와 크기만 쓴다 (정렬은 sort로 정한다) */
	Page<Product> searchForCustomer(ProductSearchCondition condition, ProductSort sort, Pageable pageable);
}
