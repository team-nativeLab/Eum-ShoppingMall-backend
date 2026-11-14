package com.example.shopping.domain.product.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shopping.domain.order.entity.OrderItem;
import com.example.shopping.domain.order.entity.OrderItemStatus;
import com.example.shopping.domain.order.entity.OrderStatus;
import com.example.shopping.domain.product.entity.ProductReview;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

	/** 고객에게 보이는 리뷰. 정렬은 서비스가 정한다. */
	Page<ProductReview> findByProductIdAndVisibleTrue(Long productId, Pageable pageable);

	/** 보이는 리뷰의 별점별 수. [별점, 수] */
	@Query("""
			select r.rating, count(r) from ProductReview r
			where r.product.id = :productId and r.visible = true group by r.rating
			""")
	List<Object[]> countVisibleByRating(@Param("productId") Long productId);

	boolean existsByOrderItemId(Long orderItemId);

	/** 관리자 목록. 상품·보임 여부·별점으로 거를 수 있고 상품 이름을 함께 읽는다. */
	@Query(value = """
			select r from ProductReview r join fetch r.product p
			where (:productId is null or p.id = :productId) and (:visible is null or r.visible = :visible)
			  and (:rating is null or r.rating = :rating)
			""", countQuery = """
			select count(r) from ProductReview r
			where (:productId is null or r.product.id = :productId) and (:visible is null or r.visible = :visible)
			  and (:rating is null or r.rating = :rating)
			""")
	Page<ProductReview> search(@Param("productId") Long productId, @Param("visible") Boolean visible,
			@Param("rating") Integer rating, Pageable pageable);

	/** 회원 본인의 주문 품목(상품과 함께). 남의 품목이면 비어 있다. */
	@Query("""
			select i from OrderItem i join fetch i.order o join fetch i.sku s join fetch s.product
			where i.id = :orderItemId and o.memberId = :memberId
			""")
	Optional<OrderItem> findOrderItemOfMember(@Param("orderItemId") Long orderItemId,
			@Param("memberId") Long memberId);

	/** 리뷰를 쓸 수 있는 주문 품목: 배송이 끝났고, 취소·반품되지 않았고, 아직 리뷰가 없는 것. 최근 주문 순 */
	@Query("""
			select i from OrderItem i join fetch i.order o join fetch i.sku s join fetch s.product
			where o.memberId = :memberId and o.status = :delivered and i.status = :ordered
			  and not exists (select r.id from ProductReview r where r.orderItemId = i.id)
			order by o.createdAt desc, i.id desc
			""")
	List<OrderItem> findReviewableItems(@Param("memberId") Long memberId, @Param("delivered") OrderStatus delivered,
			@Param("ordered") OrderItemStatus ordered, Pageable pageable);
}
