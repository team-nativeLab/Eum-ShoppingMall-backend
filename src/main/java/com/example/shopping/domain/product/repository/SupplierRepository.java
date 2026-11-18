package com.example.shopping.domain.product.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.product.entity.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

	/** 최근 등록한 순 */
	List<Supplier> findAllByOrderByIdDesc();
}
