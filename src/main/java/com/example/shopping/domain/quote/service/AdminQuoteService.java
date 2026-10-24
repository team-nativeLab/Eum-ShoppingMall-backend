package com.example.shopping.domain.quote.service;

import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.quote.dto.response.AdminQuoteResponse;
import com.example.shopping.domain.quote.entity.QuoteInquiry;
import com.example.shopping.domain.quote.entity.QuoteStatus;
import com.example.shopping.domain.quote.repository.QuoteInquiryRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.validation.SortValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminQuoteService {

	private final QuoteInquiryRepository quoteInquiryRepository;

	/** 상태를 지정하지 않으면 전체를 조회한다. */
	public PageResponse<AdminQuoteResponse> getQuotes(QuoteStatus status, Pageable pageable) {
		SortValidator.requireAllowed(pageable, Set.of("id", "createdAt"));
		var page = status == null ? quoteInquiryRepository.findAll(pageable)
				: quoteInquiryRepository.findByStatus(status, pageable);
		return PageResponse.from(page.map(AdminQuoteResponse::from));
	}

	/** 잘못 바꾼 상태를 되돌릴 수 있도록 단계 순서(접수→처리→완료)는 강제하지 않는다. */
	@Transactional
	public AdminQuoteResponse changeStatus(Long quoteId, QuoteStatus status) {
		QuoteInquiry quote = quoteInquiryRepository.findById(quoteId)
				.orElseThrow(() -> new BusinessException(ErrorCode.QUOTE_NOT_FOUND));
		quote.changeStatus(status);
		return AdminQuoteResponse.from(quote);
	}
}
