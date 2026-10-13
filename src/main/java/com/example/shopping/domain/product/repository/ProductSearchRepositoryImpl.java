package com.example.shopping.domain.product.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.example.shopping.domain.product.entity.Product;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public class ProductSearchRepositoryImpl implements ProductSearchRepository {

	private static final char LIKE_ESCAPE = '\\';

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public Page<Product> searchForCustomer(ProductSearchCondition condition, ProductSort sort, Pageable pageable) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();

		CriteriaQuery<Product> query = cb.createQuery(Product.class);
		Root<Product> product = query.from(Product.class);
		query.select(product)
				.where(predicates(cb, product, condition))
				.orderBy(orders(cb, product, sort));
		List<Product> content = entityManager.createQuery(query)
				.setFirstResult(Math.toIntExact(pageable.getOffset()))
				.setMaxResults(pageable.getPageSize())
				.getResultList();

		CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
		Root<Product> counted = countQuery.from(Product.class);
		countQuery.select(cb.count(counted)).where(predicates(cb, counted, condition));
		long total = entityManager.createQuery(countQuery).getSingleResult();

		return new PageImpl<>(content, pageable, total);
	}

	private Predicate[] predicates(CriteriaBuilder cb, Root<Product> product, ProductSearchCondition condition) {
		List<Predicate> predicates = new ArrayList<>();
		predicates.add(product.get("status").in(condition.statuses()));

		if (condition.categoryId() != null) {
			predicates.add(cb.equal(product.get("category").get("id"), condition.categoryId()));
		}
		if (condition.keyword() != null) {
			String pattern = containsPattern(condition.keyword());
			predicates.add(cb.or(
					cb.like(cb.lower(product.get("name")), pattern, LIKE_ESCAPE),
					cb.like(cb.lower(product.get("brand")), pattern, LIKE_ESCAPE)));
		}
		if (condition.brands() != null && !condition.brands().isEmpty()) {
			predicates.add(product.get("brand").in(condition.brands()));
		}
		if (condition.hasPriceRange()) {
			// 금액 미표시 상품은 가격대 검색에서 아예 뺀다.
			// 포함시키면 범위를 좁혀 가며 검색하는 것만으로 숨긴 가격을 알아낼 수 있다.
			predicates.add(cb.isTrue(product.get("priceVisible")));
			if (condition.minPrice() != null) {
				predicates.add(cb.greaterThanOrEqualTo(product.get("basePrice"), condition.minPrice()));
			}
			if (condition.maxPrice() != null) {
				predicates.add(cb.lessThanOrEqualTo(product.get("basePrice"), condition.maxPrice()));
			}
		}
		if (condition.certifiedOnly()) {
			predicates.add(cb.isTrue(product.get("safetyCertified")));
		}
		return predicates.toArray(Predicate[]::new);
	}

	/** 어떤 정렬이든 마지막은 최근 등록순(id 역순)으로 두어, 값이 같은 상품의 순서가 페이지마다 흔들리지 않게 한다. */
	private List<Order> orders(CriteriaBuilder cb, Root<Product> product, ProductSort sort) {
		List<Order> orders = new ArrayList<>();
		switch (sort) {
			case RECOMMENDED -> orders.add(cb.desc(product.get("recommendScore")));
			case POPULAR -> orders.add(cb.desc(product.get("salesCount")));
			case PRICE_ASC, PRICE_DESC -> {
				// 금액 미표시 상품은 순서로 가격대를 짐작할 수 없도록 항상 맨 뒤에 두고, 그들끼리는 가격과 무관하게 둔다.
				Expression<Boolean> priceVisible = product.get("priceVisible");
				orders.add(cb.asc(cb.selectCase().when(cb.isTrue(priceVisible), 0).otherwise(1)));
				Expression<Object> visiblePrice = cb.selectCase()
						.when(cb.isTrue(priceVisible), product.get("basePrice"))
						.otherwise(0L);
				orders.add(sort == ProductSort.PRICE_ASC ? cb.asc(visiblePrice) : cb.desc(visiblePrice));
			}
		}
		orders.add(cb.desc(product.get("id")));
		return orders;
	}

	/** 검색어에 든 %와 _가 "아무 글자"로 해석되지 않고 글자 그대로 검색되게 한다. */
	private String containsPattern(String keyword) {
		String escaped = keyword.toLowerCase(Locale.ROOT)
				.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
		return "%" + escaped + "%";
	}
}
