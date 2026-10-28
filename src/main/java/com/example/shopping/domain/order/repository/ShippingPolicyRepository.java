package com.example.shopping.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.order.entity.ShippingPolicy;

public interface ShippingPolicyRepository extends JpaRepository<ShippingPolicy, Long> {
}
