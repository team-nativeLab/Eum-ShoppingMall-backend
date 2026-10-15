package com.example.shopping.domain.product.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductSearchRepository {

	/** 관리자용 검색. 카테고리·검색어는 선택 조건이며 넘겨준 상태의 상품만 조회한다. */
	@Query("""
			select p from Product p
			where p.status in :statuses
			  and (:categoryId is null or p.category.id = :categoryId)
			  and (:keyword is null or p.name like concat('%', :keyword, '%'))
			""")
	Page<Product> search(@Param("statuses") Collection<ProductStatus> statuses, @Param("categoryId") Long categoryId,
			@Param("keyword") String keyword, Pageable pageable);

	/** 홈 화면의 신상품 */
	List<Product> findTop8ByStatusInOrderByIdDesc(Collection<ProductStatus> statuses);

	boolean existsByCategoryIdAndStatusNot(Long categoryId, ProductStatus status);

	/**
	 * 옵션이 속한 상품의 누적 판매 수량을 더한다(결제 완료). 주문 취소 때는 음수를 넘겨 뺀다.
	 * 어긋난 데이터가 있어도 0 아래로는 내려가지 않게 한다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Product p
			set p.salesCount = case when p.salesCount + :quantity < 0 then 0 else p.salesCount + :quantity end
			where p.id = (select s.product.id from ProductSku s where s.id = :skuId)
			""")
	int addSalesCount(@Param("skuId") Long skuId, @Param("quantity") long quantity);

	/** 카테고리를 지우기 전에, 그 카테고리에 남아 있는 소프트 삭제 상품의 연결을 끊는다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Product p set p.category = null
			where p.category.id = :categoryId
			  and p.status = com.example.shopping.domain.product.entity.ProductStatus.DELETED
			""")
	int detachDeletedProductsFromCategory(@Param("categoryId") Long categoryId);
}
