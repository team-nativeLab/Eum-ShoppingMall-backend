package com.example.shopping.domain.product.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.product.dto.CategoryResponse;
import com.example.shopping.domain.product.dto.ProductDetailResponse;
import com.example.shopping.domain.product.dto.ProductSummaryResponse;
import com.example.shopping.domain.product.entity.Category;
import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.domain.product.repository.CategoryRepository;
import com.example.shopping.domain.product.repository.ProductRepository;
import com.example.shopping.domain.product.repository.ProductSearchCondition;
import com.example.shopping.domain.product.repository.ProductSort;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.PageResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;

	public List<CategoryResponse> getCategories() {
		List<Category> visible = new ArrayList<>(categoryRepository.findByActiveTrueOrderBySortOrderAsc());

		// 상위 카테고리가 비노출이면 하위 카테고리도 함께 숨긴다. 여러 단계일 수 있어 더 빠질 것이 없을 때까지 반복한다.
		boolean removed = true;
		while (removed) {
			Set<Long> visibleIds = new HashSet<>();
			visible.forEach(category -> visibleIds.add(category.getId()));
			removed = visible.removeIf(category -> category.getParent() != null
					&& !visibleIds.contains(category.getParent().getId()));
		}
		return visible.stream()
				.map(CategoryResponse::from)
				.toList();
	}

	/**
	 * 고객용 상품 목록·검색. 모든 조건은 선택이며 함께 주면 모두 만족하는 상품만 나온다.
	 *
	 * @param query         상품명 또는 브랜드에 포함된 글자
	 * @param brands        이 중 하나의 브랜드인 상품만
	 * @param minPrice      가격 하한(원). 가격대를 지정하면 금액 미표시 상품은 결과에서 빠진다.
	 * @param certifiedOnly true면 안전 인증 제품만
	 * @param sort          recommended(기본), price_asc, price_desc, popular
	 * @param pageable      페이지 번호와 크기만 쓴다
	 */
	public PageResponse<ProductSummaryResponse> search(Long categoryId, String query, List<String> brands,
			Long minPrice, Long maxPrice, boolean certifiedOnly, String sort, Pageable pageable) {
		if ((minPrice != null && minPrice < 0) || (maxPrice != null && maxPrice < 0)
				|| (minPrice != null && maxPrice != null && minPrice > maxPrice)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		String keyword = StringUtils.hasText(query) ? query.trim() : null;
		List<String> brandNames = brands == null ? List.of()
				: brands.stream().filter(StringUtils::hasText).map(String::trim).toList();
		ProductSearchCondition condition = new ProductSearchCondition(ProductStatus.CUSTOMER_VISIBLE, categoryId,
				keyword, brandNames, minPrice, maxPrice, certifiedOnly);

		Page<Product> page = productRepository.searchForCustomer(condition, ProductSort.parse(sort),
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
		return PageResponse.from(page.map(ProductSummaryResponse::from));
	}

	/** 브랜드 필터에 보여 줄 브랜드 목록. 고객에게 보이는 상품이 있는 브랜드만 나온다. */
	public List<String> getBrands(Long categoryId) {
		return productRepository.findBrands(ProductStatus.CUSTOMER_VISIBLE, categoryId);
	}

	public ProductDetailResponse getDetail(Long productId) {
		Product product = productRepository.findById(productId)
				.filter(p -> ProductStatus.CUSTOMER_VISIBLE.contains(p.getStatus()))
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		return ProductDetailResponse.from(product);
	}
}
