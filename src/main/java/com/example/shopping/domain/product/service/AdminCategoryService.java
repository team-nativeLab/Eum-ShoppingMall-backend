package com.example.shopping.domain.product.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.product.dto.AdminCategoryResponse;
import com.example.shopping.domain.product.dto.CategoryCreateRequest;
import com.example.shopping.domain.product.dto.CategoryUpdateRequest;
import com.example.shopping.domain.product.entity.Category;
import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.domain.product.repository.CategoryRepository;
import com.example.shopping.domain.product.repository.ProductRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCategoryService {

	private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;
	private final ProductReadCache readCache;

	/** 비노출 카테고리까지 전부. 트리는 parentId로 프론트에서 조립한다. */
	public List<AdminCategoryResponse> getAll() {
		return categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
				.map(AdminCategoryResponse::from)
				.toList();
	}

	@Transactional
	public AdminCategoryResponse create(CategoryCreateRequest request) {
		Category parent = request.parentId() == null ? null : find(request.parentId());
		int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
		boolean active = request.active() == null || request.active();
		if (categoryRepository.existsBySlug(request.slug())) {
			throw new BusinessException(ErrorCode.CATEGORY_SLUG_EXISTS);
		}
		Category category = Category.create(parent, request.name().trim(), request.slug(), sortOrder, active);
		category.update(null, null, request.description(), request.iconKey(), null, null);
		AdminCategoryResponse response = AdminCategoryResponse.from(saveCheckingSlug(category));
		readCache.invalidateAfterCommit();
		return response;
	}

	@Transactional
	public AdminCategoryResponse update(Long categoryId, CategoryUpdateRequest request) {
		Category category = find(categoryId);
		if (request.slug() != null && categoryRepository.existsBySlugAndIdNot(request.slug(), categoryId)) {
			throw new BusinessException(ErrorCode.CATEGORY_SLUG_EXISTS);
		}
		String name = request.name() == null ? null : request.name().trim();
		category.update(name, request.slug(), request.description(), request.iconKey(), request.sortOrder(),
				request.active());
		AdminCategoryResponse response = AdminCategoryResponse.from(saveCheckingSlug(category));
		readCache.invalidateAfterCommit();
		return response;
	}

	/** 하위 카테고리나 소속 상품이 있으면 거부한다. 소프트 삭제된 상품은 소속 상품으로 세지 않는다. */
	@Transactional
	public void delete(Long categoryId) {
		if (!categoryRepository.existsById(categoryId)) {
			throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
		}
		if (categoryRepository.existsByParentId(categoryId)
				|| productRepository.existsByCategoryIdAndStatusNot(categoryId, ProductStatus.DELETED)) {
			throw new BusinessException(ErrorCode.CATEGORY_NOT_EMPTY);
		}
		productRepository.detachDeletedProductsFromCategory(categoryId);
		try {
			categoryRepository.deleteById(categoryId);
			categoryRepository.flush();
			readCache.invalidateAfterCommit();
		} catch (DataIntegrityViolationException e) {
			// 위의 검사와 삭제 사이에 다른 요청이 하위 카테고리나 상품을 추가한 경우
			throw new BusinessException(ErrorCode.CATEGORY_NOT_EMPTY);
		}
	}

	/** 위의 중복 검사와 저장 사이에 다른 요청이 같은 주소 이름을 먼저 쓴 경우도 409로 돌려준다. */
	private Category saveCheckingSlug(Category category) {
		try {
			return categoryRepository.saveAndFlush(category);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.CATEGORY_SLUG_EXISTS);
		}
	}

	private Category find(Long categoryId) {
		return categoryRepository.findById(categoryId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
	}
}
