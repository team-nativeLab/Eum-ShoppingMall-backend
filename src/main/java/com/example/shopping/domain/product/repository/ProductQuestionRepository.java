package com.example.shopping.domain.product.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shopping.domain.product.entity.ProductQuestion;
import com.example.shopping.domain.product.entity.ProductQuestionStatus;

public interface ProductQuestionRepository extends JpaRepository<ProductQuestion, Long> {

	/** 상품 상세의 문의 목록. 최근 순 */
	@Query("select q from ProductQuestion q where q.product.id = :productId order by q.createdAt desc, q.id desc")
	Page<ProductQuestion> findByProductId(@Param("productId") Long productId, Pageable pageable);

	/** 관리자 목록. 상태·상품으로 거를 수 있고 상품 이름을 함께 읽는다. */
	@Query(value = """
			select q from ProductQuestion q join fetch q.product p
			where (:status is null or q.status = :status) and (:productId is null or p.id = :productId)
			""", countQuery = """
			select count(q) from ProductQuestion q
			where (:status is null or q.status = :status) and (:productId is null or q.product.id = :productId)
			""")
	Page<ProductQuestion> search(@Param("status") ProductQuestionStatus status, @Param("productId") Long productId,
			Pageable pageable);
}
