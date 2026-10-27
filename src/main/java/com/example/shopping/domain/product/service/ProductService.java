package com.example.shopping.domain.product.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;

import com.example.shopping.domain.product.dto.response.CategoryResponse;
import com.example.shopping.domain.product.dto.response.ProductDetailResponse;
import com.example.shopping.domain.product.dto.response.ProductFacetsResponse;
import com.example.shopping.domain.product.dto.response.ProductFacetsResponse.FacetItem;
import com.example.shopping.domain.product.dto.request.ProductSearchRequest;
import com.example.shopping.domain.product.dto.response.ProductSuggestionResponse;
import com.example.shopping.domain.product.dto.response.ProductSummaryResponse;
import com.example.shopping.domain.product.entity.Category;
import com.example.shopping.domain.product.entity.Certification;
import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.domain.product.repository.CategoryRepository;
import com.example.shopping.domain.product.repository.ProductRepository;
import com.example.shopping.domain.product.repository.ProductSearchCondition;
import com.example.shopping.domain.product.repository.ProductSort;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.validation.SortValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {
	private static final TypeReference<List<CategoryResponse>> CATEGORY_LIST = new TypeReference<>() {};
	private static final TypeReference<List<ProductSuggestionResponse>> SUGGESTION_LIST = new TypeReference<>() {};
	private static final TypeReference<PageResponse<ProductSummaryResponse>> SEARCH_PAGE = new TypeReference<>() {};

	/** 검색창 자동 완성에 보여 줄 최대 개수 */
	private static final int SUGGESTION_LIMIT = 5;

	/** 검색어 길이와 한 번에 고를 수 있는 브랜드 수. 매우 긴 검색어나 수천 개의 브랜드로 DB에 부하를 주지 못하게 한다. */
	private static final int MAX_KEYWORD_LENGTH = 100;
	private static final int MAX_BRANDS = 50;

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final ProductReadCache readCache;

	public List<CategoryResponse> getCategories() {
		return readCache.getOrLoad(new ReadKey("categories", null, null), CATEGORY_LIST,
				() -> visibleCategories().stream().map(CategoryResponse::from).toList());
	}

	/** 고객용 상품 목록·검색. 조건의 뜻은 ProductSearchRequest 참고 */
	public PageResponse<ProductSummaryResponse> search(ProductSearchRequest request, Pageable pageable) {
		SortValidator.requirePageInRange(pageable);
		Long minPrice = request.minPrice();
		Long maxPrice = request.maxPrice();
		if ((minPrice != null && minPrice < 0) || (maxPrice != null && maxPrice < 0)
				|| (minPrice != null && maxPrice != null && minPrice > maxPrice)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		List<String> brandNames = request.brand() == null ? List.of()
				: request.brand().stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
		if (brandNames.size() > MAX_BRANDS) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		Set<Certification> certifications = request.certification() == null ? Set.of()
				: Set.copyOf(request.certification());
		String keyword = keyword(request.query());
		ProductSort sort = ProductSort.parse(request.sort());
		int pageNumber = pageable.getPageNumber();
		int pageSize = pageable.getPageSize();
		SearchKey key = new SearchKey(request.categoryId(), keyword, brandNames.stream().sorted().toList(), minPrice,
				maxPrice, Boolean.TRUE.equals(request.certified()), certifications.stream().sorted().toList(),
				Boolean.TRUE.equals(request.excludeSoldOut()), sort, pageNumber, pageSize);
		return readCache.getOrLoad(key, SEARCH_PAGE, () -> {
			ProductSearchCondition condition = new ProductSearchCondition(ProductStatus.CUSTOMER_VISIBLE,
					categoryScope(request.categoryId()), keyword, brandNames, minPrice, maxPrice,
					certifications, Boolean.TRUE.equals(request.certified()),
					Boolean.TRUE.equals(request.excludeSoldOut()));
			Page<Product> page = productRepository.searchForCustomer(condition, sort,
					PageRequest.of(pageNumber, pageSize));
			return PageResponse.from(page.map(ProductSummaryResponse::from));
		});
	}

	/**
	 * 검색 화면의 카테고리 탭과 필터(브랜드, 인증)에 보여 줄 항목별 상품 수.
	 * 가격·브랜드·인증·품절 필터는 개수에 반영하지 않는다 (필터를 골라도 다른 선택지의 개수가 바뀌지 않게).
	 */
	public ProductFacetsResponse getFacets(Long categoryId, String query) {
		String keyword = keyword(query);
		return readCache.getOrLoad(new ReadKey("facets", categoryId, keyword), ProductFacetsResponse.class,
				() -> loadFacets(categoryId, keyword));
	}

	private ProductFacetsResponse loadFacets(Long categoryId, String keyword) {
		List<Category> all = categoryRepository.findAll();
		ProductSearchCondition keywordOnly = ProductSearchCondition.of(ProductStatus.CUSTOMER_VISIBLE, null, keyword);
		ProductSearchCondition scoped = ProductSearchCondition.of(ProductStatus.CUSTOMER_VISIBLE,
				withDescendants(all, categoryId), keyword);

		long keywordTotalCount = productRepository.count(keywordOnly);
		long totalCount = categoryId == null ? keywordTotalCount : productRepository.count(scoped);
		return new ProductFacetsResponse(totalCount, keywordTotalCount,
				rootCategoryFacets(all, productRepository.countByCategory(keywordOnly)),
				facets(productRepository.countByBrand(scoped), Function.identity(), Function.identity()),
				facets(productRepository.countByCertification(scoped), Certification::name,
						Certification::getLabel));
	}

	/** 검색창 자동 완성. 상품명이나 브랜드에 검색어가 든 상품을 추천순으로 최대 5개 */
	public List<ProductSuggestionResponse> suggest(String query) {
		String keyword = keyword(query);
		if (keyword == null) {
			return List.of();
		}
		return readCache.getOrLoad(new ReadKey("suggestions", null, keyword), SUGGESTION_LIST, () -> {
			ProductSearchCondition condition = ProductSearchCondition.of(ProductStatus.CUSTOMER_VISIBLE, null, keyword);
			return productRepository.findTop(condition, ProductSort.RECOMMENDED, SUGGESTION_LIMIT).stream()
					.map(ProductSuggestionResponse::from).toList();
		});
	}


	public ProductDetailResponse getDetail(Long productId) {
		Product product = productRepository.findById(productId)
				.filter(p -> ProductStatus.CUSTOMER_VISIBLE.contains(p.getStatus()))
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		return ProductDetailResponse.from(product);
	}

	/** 노출 중인 카테고리. 상위 카테고리가 비노출이면 하위 카테고리도 함께 숨긴다. */
	private List<Category> visibleCategories() {
		List<Category> visible = new ArrayList<>(categoryRepository.findByActiveTrueOrderBySortOrderAsc());

		// 여러 단계일 수 있어 더 빠질 것이 없을 때까지 반복한다.
		boolean removed = true;
		while (removed) {
			Set<Long> visibleIds = new HashSet<>();
			visible.forEach(category -> visibleIds.add(category.getId()));
			removed = visible.removeIf(category -> category.getParent() != null
					&& !visibleIds.contains(category.getParent().getId()));
		}
		return visible;
	}

	/** 상품 검색의 카테고리 조건. 카테고리를 고르지 않았으면 null(조건 없음) */
	private Set<Long> categoryScope(Long categoryId) {
		return categoryId == null ? null : withDescendants(categoryRepository.findAll(), categoryId);
	}

	/** 카테고리와 그 아래의 모든 하위 카테고리 ID. categoryId가 null이면 카테고리 조건 없음(null) */
	private Set<Long> withDescendants(List<Category> all, Long categoryId) {
		if (categoryId == null) {
			return null;
		}
		Map<Long, List<Long>> childrenByParent = new HashMap<>();
		for (Category category : all) {
			if (category.getParent() != null) {
				childrenByParent.computeIfAbsent(category.getParent().getId(), id -> new ArrayList<>())
						.add(category.getId());
			}
		}
		Set<Long> ids = new HashSet<>();
		Deque<Long> pending = new ArrayDeque<>(List.of(categoryId));
		while (!pending.isEmpty()) {
			Long id = pending.pop();
			if (ids.add(id)) {
				pending.addAll(childrenByParent.getOrDefault(id, List.of()));
			}
		}
		return ids;
	}

	/**
	 * 카테고리별 상품 수를 최상위 카테고리로 합친다. 비노출 최상위 카테고리는 뺀다.
	 * (상위가 노출 중이 아니면 하위도 숨기므로, 최상위 카테고리의 노출 여부만 보면 된다.)
	 */
	private List<FacetItem<Long>> rootCategoryFacets(List<Category> all, Map<Long, Long> countByCategory) {
		Map<Long, Category> byId = new HashMap<>();
		all.forEach(category -> byId.put(category.getId(), category));

		Map<Category, Long> countByRoot = new HashMap<>();
		countByCategory.forEach((categoryId, count) -> {
			Category root = rootOf(byId, categoryId);
			if (root != null && root.getParent() == null && root.isActive()) {
				countByRoot.merge(root, count, Long::sum);
			}
		});
		return facets(countByRoot, Category::getId, Category::getName);
	}

	/** 상위로 거슬러 올라가 최상위 카테고리를 찾는다. 잘못된 데이터로 순환하더라도 멈추도록 지나온 카테고리를 기억한다. */
	private Category rootOf(Map<Long, Category> byId, Long categoryId) {
		Category current = byId.get(categoryId);
		Set<Long> visited = new HashSet<>();
		while (current != null && current.getParent() != null && visited.add(current.getId())) {
			current = byId.get(current.getParent().getId());
		}
		return current;
	}

	/** 상품 수가 많은 순, 같으면 이름순으로 정렬한 필터 항목 */
	private <K, V> List<FacetItem<V>> facets(Map<K, Long> counts, Function<K, V> value, Function<K, String> label) {
		return counts.entrySet().stream()
				.map(entry -> new FacetItem<>(value.apply(entry.getKey()), label.apply(entry.getKey()),
						entry.getValue()))
				.sorted(Comparator.comparingLong((FacetItem<V> item) -> item.count()).reversed()
						.thenComparing(FacetItem::label))
				.toList();
	}

	private static String keyword(String query) {
		if (!StringUtils.hasText(query)) {
			return null;
		}
		String keyword = query.trim();
		if (keyword.length() > MAX_KEYWORD_LENGTH) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return keyword;
	}

	private record ReadKey(String operation, Long categoryId, String query) {
	}

	private record SearchKey(Long categoryId, String query, List<String> brands, Long minPrice, Long maxPrice,
			boolean certified, List<Certification> certifications, boolean excludeSoldOut, ProductSort sort, int page,
			int size) {
	}
}
