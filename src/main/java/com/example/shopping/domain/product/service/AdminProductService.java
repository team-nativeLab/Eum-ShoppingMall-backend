package com.example.shopping.domain.product.service;

import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.product.dto.AdminProductDetailResponse;
import com.example.shopping.domain.product.dto.AdminProductSummaryResponse;
import com.example.shopping.domain.product.dto.ProductCreateRequest;
import com.example.shopping.domain.product.dto.ProductSkuRequest;
import com.example.shopping.domain.product.dto.ProductUpdateRequest;
import com.example.shopping.domain.product.entity.Category;
import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductSku;
import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.domain.product.repository.CategoryRepository;
import com.example.shopping.domain.product.repository.ProductRepository;
import com.example.shopping.domain.product.repository.ProductSkuRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.validation.SortValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProductService {

	private final ProductRepository productRepository;
	private final ProductSkuRepository productSkuRepository;
	private final CategoryRepository categoryRepository;
	private final ProductReadCache readCache;

	/** 상태를 지정하지 않으면 삭제된 상품을 뺀 전체(판매중·품절·비공개)를 조회한다. */
	public PageResponse<AdminProductSummaryResponse> search(ProductStatus status, Long categoryId, String keyword,
			Pageable pageable) {
		SortValidator.requireAllowed(pageable, Product.SORTABLE);
		Set<ProductStatus> statuses = status == null ? ProductStatus.MANAGEABLE : Set.of(requireManageable(status));
		String trimmed = StringUtils.hasText(keyword) ? keyword.trim() : null;
		return PageResponse.from(productRepository.search(statuses, categoryId, trimmed, pageable)
				.map(AdminProductSummaryResponse::from));
	}

	public AdminProductDetailResponse getDetail(Long productId) {
		return AdminProductDetailResponse.from(find(productId));
	}

	@Transactional
	public AdminProductDetailResponse create(ProductCreateRequest request) {
		ProductStatus status = request.status() == null ? ProductStatus.ON_SALE : requireManageable(request.status());
		boolean priceVisible = request.priceVisible() == null || request.priceVisible();
		Product product = Product.create(findCategory(request.categoryId()), request.name().trim(),
				request.description(), request.imageUrl(), request.basePrice(), request.listPrice(), priceVisible,
				status);
		product.updateSearchAttributes(request.brand(), request.certifications(), request.recommendScore());
		if (request.skus() != null) {
			request.skus().forEach(sku -> product.addSku(sku.optionName().trim(), sku.price(), sku.stock()));
		}
		AdminProductDetailResponse response = AdminProductDetailResponse.from(productRepository.save(product));
		readCache.invalidateAfterCommit();
		return response;
	}

	@Transactional
	public AdminProductDetailResponse update(Long productId, ProductUpdateRequest request) {
		Product product = find(productId);
		ProductStatus status = request.status() == null ? null : requireManageable(request.status());
		String name = request.name() == null ? null : request.name().trim();
		product.update(findCategory(request.categoryId()), name, request.description(), request.imageUrl(),
				request.basePrice(), request.listPrice(), status);
		product.updateSearchAttributes(request.brand(), request.certifications(), request.recommendScore());

		if (request.skus() != null) {
			for (ProductSkuRequest sku : request.skus()) {
				if (sku.id() == null) {
					product.addSku(sku.optionName().trim(), sku.price(), sku.stock());
				} else {
					findSku(product, sku.id()).update(sku.optionName().trim(), sku.price(), sku.stock());
				}
			}
		}
		// 새로 추가된 옵션의 ID와 갱신된 수정 시각을 응답에 싣기 위해 먼저 DB에 반영한다.
		AdminProductDetailResponse response = AdminProductDetailResponse.from(productRepository.saveAndFlush(product));
		readCache.invalidateAfterCommit();
		return response;
	}

	/**
	 * 입고: 옵션의 재고에 수량을 더한다. 상품 수정처럼 최종 수량을 통째로 덮어쓰는 방식이 아니라서
	 * 입고 처리 중에 주문이 들어와도 충돌하지 않는다. 상품의 판매 상태(품절 등)는 바꾸지 않는다.
	 */
	@Transactional
	public AdminProductDetailResponse receiveStock(Long productId, Long skuId, int quantity) {
		findSku(find(productId), skuId);
		if (productSkuRepository.receiveStock(skuId, quantity, ProductSku.MAX_STOCK - quantity) == 0) {
			throw new BusinessException(ErrorCode.STOCK_LIMIT_EXCEEDED);
		}
		readCache.invalidateAfterCommit();
		// 위 쿼리가 영속성 컨텍스트를 비우므로 더해진 재고가 반영된 상태로 다시 읽는다.
		return AdminProductDetailResponse.from(find(productId));
	}

	@Transactional
	public AdminProductDetailResponse changePriceVisibility(Long productId, boolean priceVisible) {
		Product product = find(productId);
		product.changePriceVisibility(priceVisible);
		readCache.invalidateAfterCommit();
		return AdminProductDetailResponse.from(product);
	}

	@Transactional
	public void delete(Long productId) {
		find(productId).delete();
		readCache.invalidateAfterCommit();
	}

	/** 이미 삭제된 상품은 없는 상품으로 취급한다. */
	private Product find(Long productId) {
		return productRepository.findById(productId)
				.filter(product -> ProductStatus.MANAGEABLE.contains(product.getStatus()))
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
	}

	private ProductSku findSku(Product product, Long skuId) {
		return product.getSkus().stream()
				.filter(sku -> sku.getId().equals(skuId))
				.findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.SKU_NOT_FOUND));
	}

	private Category findCategory(Long categoryId) {
		if (categoryId == null) {
			return null;
		}
		return categoryRepository.findById(categoryId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
	}

	/** DELETED는 삭제 API로만 바꿀 수 있고, 직접 지정하거나 검색 조건으로 쓸 수 없다. */
	private ProductStatus requireManageable(ProductStatus status) {
		if (!ProductStatus.MANAGEABLE.contains(status)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return status;
	}
}
