package com.example.shopping.domain.product.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.product.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

	List<Category> findByActiveTrueOrderBySortOrderAsc();

	List<Category> findAllByOrderBySortOrderAscIdAsc();

	boolean existsByParentId(Long parentId);

	boolean existsBySlug(String slug);

	boolean existsBySlugAndIdNot(String slug, Long id);
}
