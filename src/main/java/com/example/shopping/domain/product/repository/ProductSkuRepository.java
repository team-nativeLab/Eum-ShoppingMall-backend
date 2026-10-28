package com.example.shopping.domain.product.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shopping.domain.product.entity.ProductSku;

public interface ProductSkuRepository extends JpaRepository<ProductSku, Long> {

	/** 상품까지 함께 읽는다. 잠그지 않는다. */
	@Query("select s from ProductSku s join fetch s.product where s.id = :id")
	Optional<ProductSku> findWithProductById(@Param("id") Long id);

	/**
	 * 재고가 충분할 때만 차감한다. 0을 돌려주면 재고 부족이다.
	 * 확인과 차감을 한 문장으로 처리하므로 동시에 주문이 몰려도 재고보다 많이 팔리지 않는다.
	 * 관리자가 같은 옵션을 수정 중이면 그 저장이 충돌로 거절되도록 version도 올린다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update ProductSku s set s.stock = s.stock - :quantity, s.version = s.version + 1
			where s.id = :id and s.active = true and s.stock >= :quantity
			""")
	int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);

	/**
	 * 재고를 현재 값에서 늘리거나 줄인다(입고·파손·실사 차이). 최종 수량으로 덮어쓰지 않으므로 그사이 들어온 주문 차감을 지우지 않는다.
	 * 0 아래나 상한 위가 되면 바꾸지 않고 0을 돌려준다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update ProductSku s set s.stock = s.stock + :delta, s.version = s.version + 1
			where s.id = :id and s.stock + :delta >= 0 and s.stock + :delta <= :maxStock
			""")
	int adjustStock(@Param("id") Long id, @Param("delta") int delta, @Param("maxStock") int maxStock);

	/** 주문 만료·취소로 재고를 되돌린다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update ProductSku s set s.stock = s.stock + :quantity, s.version = s.version + 1 where s.id = :id")
	int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);
}
