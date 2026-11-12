package com.example.shopping.domain.product.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shopping.domain.product.entity.Brand;
import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.domain.upload.service.ImageReferences;

public interface BrandRepository extends JpaRepository<Brand, Long>, ImageReferences {

	List<Brand> findAllByOrderBySortOrderAscNameAsc();

	List<Brand> findByVisibleTrueOrderBySortOrderAscNameAsc();

	Optional<Brand> findFirstByVisibleTrueAndFeaturedTrueOrderBySortOrderAscIdAsc();

	boolean existsByName(String name);

	boolean existsByNameAndIdNot(String name, Long id);

	/** 브랜드 이름별 상품 수. [이름, 수] */
	@Query("""
			select p.brand, count(p) from Product p
			where p.brand in :names and p.status in :statuses group by p.brand
			""")
	List<Object[]> countProductsByBrand(@Param("names") Collection<String> names,
			@Param("statuses") Collection<ProductStatus> statuses);

	@Query("select b.logoUrl from Brand b where b.logoUrl is not null")
	List<String> findLogoUrls();

	@Query("select b.coverImageUrl from Brand b where b.coverImageUrl is not null")
	List<String> findCoverImageUrls();

	/** 참조 없는 이미지 정리가 브랜드 로고·대표 이미지를 지우지 않도록 알려 준다. */
	@Override
	default List<String> findAllImageUrls() {
		List<String> urls = new ArrayList<>(findLogoUrls());
		urls.addAll(findCoverImageUrls());
		return urls;
	}
}
