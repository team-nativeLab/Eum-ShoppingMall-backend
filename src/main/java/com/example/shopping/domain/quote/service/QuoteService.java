package com.example.shopping.domain.quote.service;

import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.domain.product.repository.ProductRepository;
import com.example.shopping.domain.quote.dto.request.QuoteCreateRequest;
import com.example.shopping.domain.quote.dto.response.QuoteResponse;
import com.example.shopping.domain.quote.entity.QuoteInquiry;
import com.example.shopping.domain.quote.repository.QuoteInquiryRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.validation.SortValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuoteService {

	private final QuoteInquiryRepository quoteInquiryRepository;
	private final ProductRepository productRepository;

	/** @param memberId 로그인한 회원의 ID. 비회원이면 null */
	@Transactional
	public QuoteResponse create(Long memberId, QuoteCreateRequest request) {
		if (request.productId() != null) {
			// 고객에게 보이지 않는 상품(비공개·삭제)은 없는 상품과 똑같이 응답한다.
			productRepository.findById(request.productId())
					.filter(product -> ProductStatus.CUSTOMER_VISIBLE.contains(product.getStatus()))
					.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		}
		String email = StringUtils.hasText(request.email()) ? request.email().trim() : null;
		QuoteInquiry quote = QuoteInquiry.create(memberId, request.productId(), request.companyName().trim(),
				request.managerName().trim(), request.phone().replace("-", ""), email, request.content());
		return QuoteResponse.from(quoteInquiryRepository.save(quote));
	}

	/** 본인이 로그인 상태로 접수한 견적 문의만 조회된다. */
	public PageResponse<QuoteResponse> getMyQuotes(Long memberId, Pageable pageable) {
		SortValidator.requireAllowed(pageable, Set.of("id", "createdAt"));
		return PageResponse.from(quoteInquiryRepository.findByMemberId(memberId, pageable).map(QuoteResponse::from));
	}
}
