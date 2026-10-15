package com.example.shopping.domain.product.repository;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.shopping.domain.product.entity.Certification;
import com.example.shopping.domain.product.entity.Product;

/** 조건과 정렬이 요청마다 달라지는 고객용 상품 검색. 구현은 ProductSearchRepositoryImpl */
public interface ProductSearchRepository {

	/** @param pageable 페이지 번호와 크기만 쓴다 (정렬은 sort로 정한다) */
	Page<Product> searchForCustomer(ProductSearchCondition condition, ProductSort sort, Pageable pageable);

	/** 전체 개수를 세지 않고 앞에서부터 limit개만 가져온다. 검색어 자동 완성처럼 개수가 필요 없을 때 쓴다. */
	List<Product> findTop(ProductSearchCondition condition, ProductSort sort, int limit);

	long count(ProductSearchCondition condition);

	/** 카테고리 ID별 상품 수. 카테고리가 없는 상품은 세지 않는다. */
	Map<Long, Long> countByCategory(ProductSearchCondition condition);

	/** 브랜드별 상품 수. 브랜드가 없는 상품은 세지 않는다. */
	Map<String, Long> countByBrand(ProductSearchCondition condition);

	/** 인증 종류별 상품 수. 인증을 여러 개 받은 상품은 각 인증에 한 번씩 센다. */
	Map<Certification, Long> countByCertification(ProductSearchCondition condition);
}
