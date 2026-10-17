package com.example.shopping.domain.cart.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shopping.domain.cart.entity.CartItem;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

	/** 옵션·상품까지 한 번에 가져온다 (품목마다 추가 조회가 나가지 않도록). */
	@Query("""
			select c from CartItem c join fetch c.sku s join fetch s.product
			where c.memberId = :memberId
			order by c.id
			""")
	List<CartItem> findAllWithProduct(@Param("memberId") Long memberId);

	Optional<CartItem> findByMemberIdAndSkuId(Long memberId, Long skuId);

	long countByMemberId(Long memberId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from CartItem c where c.memberId = :memberId and c.sku.id in :skuIds")
	int deleteByMemberIdAndSkuIds(@Param("memberId") Long memberId, @Param("skuIds") Collection<Long> skuIds);
}
