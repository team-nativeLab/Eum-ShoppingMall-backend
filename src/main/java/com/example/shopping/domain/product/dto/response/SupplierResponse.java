package com.example.shopping.domain.product.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.product.entity.Supplier;
import com.example.shopping.domain.product.entity.SupplierOrderChannel;

/**
 * 관리자 발주처(#208). 담당자 연락처가 들어 있어 관리자만 본다.
 *
 * @param orderEmail 주문 수신 방식이 EMAIL일 때만 있다. 아니면 null
 * @param orderFax   주문 수신 방식이 FAX일 때만 있다. 아니면 null
 */
public record SupplierResponse(Long id, String companyName, String contactName, String contactPhone,
		String contactEmail, SupplierOrderChannel orderChannel, String orderEmail, String orderFax, String address,
		String memo, LocalDateTime updatedAt) {

	public static SupplierResponse from(Supplier supplier) {
		return new SupplierResponse(supplier.getId(), supplier.getCompanyName(), supplier.getContactName(),
				supplier.getContactPhone(), supplier.getContactEmail(), supplier.getOrderChannel(),
				supplier.getOrderEmail(), supplier.getOrderFax(), supplier.getAddress(), supplier.getMemo(),
				supplier.getUpdatedAt());
	}
}
