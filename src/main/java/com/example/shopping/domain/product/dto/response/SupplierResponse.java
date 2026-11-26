package com.example.shopping.domain.product.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.product.entity.Supplier;
import com.example.shopping.domain.product.entity.SupplierOrderChannel;

/**
 * 관리자 발주처(#208). 담당자 연락처가 들어 있어 관리자만 본다.
 *
 * @param id           발주처 ID
 * @param companyName  회사명
 * @param contactName  담당자 이름
 * @param contactPhone 담당자 연락처
 * @param contactEmail 담당자 이메일
 * @param orderChannel 주문(발주) 정보를 받는 방식. EMAIL(이메일) 또는 FAX(팩스)
 * @param orderEmail   주문 수신 방식이 EMAIL일 때만 있다. 아니면 null
 * @param orderFax     주문 수신 방식이 FAX일 때만 있다. 아니면 null
 * @param address      주소. 없으면 빈 문자열
 * @param memo         관리자 메모. 없으면 빈 문자열
 * @param updatedAt    마지막 수정 시각
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
