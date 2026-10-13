package com.example.shopping.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.order.entity.CheckoutDelivery;

public interface CheckoutDeliveryRepository extends JpaRepository<CheckoutDelivery, Long> {
}
