package com.example.shopping.domain.product.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.product.dto.request.SupplierRequest;
import com.example.shopping.domain.product.dto.response.SupplierResponse;
import com.example.shopping.domain.product.entity.Supplier;
import com.example.shopping.domain.product.entity.SupplierOrderChannel;
import com.example.shopping.domain.product.repository.ProductRepository;
import com.example.shopping.domain.product.repository.SupplierRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 관리자 발주처 관리(#208) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSupplierService {

	private final SupplierRepository supplierRepository;
	private final ProductRepository productRepository;

	/** 최근 등록한 순 */
	public List<SupplierResponse> list() {
		return supplierRepository.findAllByOrderByIdDesc().stream().map(SupplierResponse::from).toList();
	}

	@Transactional
	public SupplierResponse create(SupplierRequest request) {
		return SupplierResponse.from(supplierRepository.saveAndFlush(Supplier.create(fields(request))));
	}

	@Transactional
	public SupplierResponse update(Long supplierId, SupplierRequest request) {
		Supplier supplier = find(supplierId);
		supplier.change(fields(request));
		return SupplierResponse.from(supplierRepository.saveAndFlush(supplier));
	}

	/** 상품(삭제 처리된 상품 포함)에 연결된 발주처는 409 */
	@Transactional
	public void delete(Long supplierId) {
		Supplier supplier = find(supplierId);
		if (productRepository.existsBySupplierId(supplierId)) {
			throw new BusinessException(ErrorCode.SUPPLIER_IN_USE);
		}
		supplierRepository.delete(supplier);
	}

	/** 상품 등록·수정에서 고른 발주처가 있는지. 없으면 404 */
	public void requireExists(Long supplierId) {
		if (!supplierRepository.existsById(supplierId)) {
			throw new BusinessException(ErrorCode.SUPPLIER_NOT_FOUND);
		}
	}

	private Supplier find(Long supplierId) {
		return supplierRepository.findById(supplierId)
				.orElseThrow(() -> new BusinessException(ErrorCode.SUPPLIER_NOT_FOUND));
	}

	/** 수신 방식에 맞는 주소·번호가 있어야 한다. 맞지 않는 쪽 값은 저장하지 않는다(Supplier.change). */
	private static Supplier.Fields fields(SupplierRequest request) {
		String orderEmail = text(request.orderEmail());
		String orderFax = text(request.orderFax());
		if ((request.orderChannel() == SupplierOrderChannel.EMAIL && orderEmail.isEmpty())
				|| (request.orderChannel() == SupplierOrderChannel.FAX && orderFax.isEmpty())) {
			throw new BusinessException(ErrorCode.SUPPLIER_ORDER_CONTACT_REQUIRED);
		}
		return new Supplier.Fields(request.companyName().trim(), request.contactName().trim(),
				request.contactPhone().trim(), request.contactEmail().trim(), request.orderChannel(), orderEmail,
				orderFax, text(request.address()), text(request.memo()));
	}

	private static String text(String value) {
		return StringUtils.hasText(value) ? value.trim() : "";
	}
}
