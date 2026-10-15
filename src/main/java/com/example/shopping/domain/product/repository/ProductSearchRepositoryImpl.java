package com.example.shopping.domain.product.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.example.shopping.domain.product.entity.Certification;
import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductSku;
import com.example.shopping.domain.product.entity.ProductStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public class ProductSearchRepositoryImpl implements ProductSearchRepository {

	private static final char LIKE_ESCAPE = '\\';

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public Page<Product> searchForCustomer(ProductSearchCondition condition, ProductSort sort, Pageable pageable) {
		List<Product> content = find(condition, sort, Math.toIntExact(pageable.getOffset()), pageable.getPageSize());
		return new PageImpl<>(content, pageable, count(condition));
	}

	@Override
	public List<Product> findTop(ProductSearchCondition condition, ProductSort sort, int limit) {
		return find(condition, sort, 0, limit);
	}

	@Override
	public long count(ProductSearchCondition condition) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Long> query = cb.createQuery(Long.class);
		Root<Product> product = query.from(Product.class);
		query.select(cb.count(product)).where(predicates(cb, query, product, condition));
		return entityManager.createQuery(query).getSingleResult();
	}

	@Override
	public Map<Long, Long> countByCategory(ProductSearchCondition condition) {
		return countBy(condition, product -> product.get("category").get("id"));
	}

	@Override
	public Map<String, Long> countByBrand(ProductSearchCondition condition) {
		return countBy(condition, product -> product.get("brand"));
	}

	@Override
	public Map<Certification, Long> countByCertification(ProductSearchCondition condition) {
		return countBy(condition, product -> product.join("certifications"));
	}

	private List<Product> find(ProductSearchCondition condition, ProductSort sort, int offset, int limit) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Product> query = cb.createQuery(Product.class);
		Root<Product> product = query.from(Product.class);
		query.select(product)
				.where(predicates(cb, query, product, condition))
				.orderBy(orders(cb, product, sort));
		return entityManager.createQuery(query)
				.setFirstResult(offset)
				.setMaxResults(limit)
				.getResultList();
	}

	/** 조건에 맞는 상품을 key별로 센다. key가 null인 상품(카테고리·브랜드 없음)은 결과에서 뺀다. */
	private <T> Map<T, Long> countBy(ProductSearchCondition condition, Function<Root<Product>, Expression<T>> key) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<Product> product = query.from(Product.class);
		Expression<T> group = key.apply(product);
		query.multiselect(group, cb.count(product))
				.where(predicates(cb, query, product, condition))
				.groupBy(group);

		Map<T, Long> counts = new HashMap<>();
		for (Tuple row : entityManager.createQuery(query).getResultList()) {
			@SuppressWarnings("unchecked")
			T value = (T) row.get(0);
			if (value != null) {
				counts.put(value, row.get(1, Long.class));
			}
		}
		return counts;
	}

	private Predicate[] predicates(CriteriaBuilder cb, CommonAbstractCriteria query, Root<Product> product,
			ProductSearchCondition condition) {
		List<Predicate> predicates = new ArrayList<>();
		predicates.add(product.get("status").in(condition.statuses()));

		if (condition.categoryIds() != null) {
			predicates.add(product.get("category").get("id").in(condition.categoryIds()));
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
		if (condition.certifications() != null && !condition.certifications().isEmpty()) {
			Subquery<Long> certified = query.subquery(Long.class);
			Root<Product> sameProduct = certified.from(Product.class);
			certified.select(sameProduct.get("id")).where(
					cb.equal(sameProduct.get("id"), product.get("id")),
					sameProduct.join("certifications").in(condition.certifications()));
			predicates.add(cb.exists(certified));
		}
		if (condition.certifiedOnly()) {
			predicates.add(cb.isNotEmpty(product.get("certifications")));
		}
		if (condition.excludeSoldOut()) {
			predicates.add(cb.notEqual(product.get("status"), ProductStatus.SOLD_OUT));
			// Product.isSoldOut()과 같은 기준: 옵션이 있는데 재고가 남은 옵션이 하나도 없으면 품절
			predicates.add(cb.or(cb.not(cb.exists(skuOf(cb, query, product, false))),
					cb.exists(skuOf(cb, query, product, true))));
		}
		return predicates.toArray(Predicate[]::new);
	}

	/** 상품의 옵션을 찾는 하위 쿼리. inStockOnly면 재고가 남은 옵션만 */
	private Subquery<Long> skuOf(CriteriaBuilder cb, CommonAbstractCriteria query, Root<Product> product,
			boolean inStockOnly) {
		Subquery<Long> subquery = query.subquery(Long.class);
		Root<ProductSku> sku = subquery.from(ProductSku.class);
		Predicate sameProduct = cb.equal(sku.get("product").get("id"), product.get("id"));
		subquery.select(sku.get("id")).where(inStockOnly
				? cb.and(sameProduct, cb.greaterThan(sku.get("stock"), 0))
				: sameProduct);
		return subquery;
	}

	/** 어떤 정렬이든 마지막은 최근 등록순(id 역순)으로 두어, 값이 같은 상품의 순서가 페이지마다 흔들리지 않게 한다. */
	private List<Order> orders(CriteriaBuilder cb, Root<Product> product, ProductSort sort) {
		List<Order> orders = new ArrayList<>();
		switch (sort) {
			case RECOMMENDED -> orders.add(cb.desc(product.get("recommendScore")));
			case POPULAR -> orders.add(cb.desc(product.get("salesCount")));
			case NEWEST -> {
				// 아래의 id 역순이 곧 최근 등록순이다.
			}
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
