package com.example.shopping.domain.support.service;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.util.StringUtils;

import com.example.shopping.domain.support.dto.response.FaqResponse;
import com.example.shopping.domain.support.dto.request.InquiryCreateRequest;
import com.example.shopping.domain.support.dto.response.InquiryResponse;
import com.example.shopping.domain.support.dto.response.NoticeResponse;
import com.example.shopping.domain.support.entity.Inquiry;
import com.example.shopping.domain.support.repository.FaqRepository;
import com.example.shopping.domain.support.repository.InquiryRepository;
import com.example.shopping.domain.support.repository.NoticeRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.validation.SortValidator;
import com.example.shopping.global.validation.ContactFormats;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportService {

	private final NoticeRepository noticeRepository;
	private final FaqRepository faqRepository;
	private final InquiryRepository inquiryRepository;

	public PageResponse<NoticeResponse> getNotices(Pageable pageable) {
		// 공지는 항상 고정 공지 먼저, 최신순으로 내려주므로 다른 정렬 기준은 받지 않는다.
		SortValidator.requireAllowed(pageable, Set.of());
		return PageResponse.from(
				noticeRepository.findByVisibleTrueOrderByPinnedDescCreatedAtDesc(pageable).map(NoticeResponse::summary));
	}

	public NoticeResponse getNotice(Long noticeId) {
		return noticeRepository.findByIdAndVisibleTrue(noticeId)
				.map(NoticeResponse::detail)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
	}

	public List<FaqResponse> getFaqs() {
		return faqRepository.findAllByOrderBySortOrderAsc().stream()
				.map(FaqResponse::from)
				.toList();
	}

	/** @param memberId 로그인한 회원의 ID. 비회원이면 null */
	@Transactional
	public InquiryResponse createInquiry(Long memberId, InquiryCreateRequest request) {
		String phone = StringUtils.hasText(request.contactPhone()) ? ContactFormats.phone(request.contactPhone()) : null;
		String email = StringUtils.hasText(request.contactEmail()) ? request.contactEmail().trim() : null;
		if (memberId == null && phone == null && email == null) {
			// 비회원은 로그인해서 답변을 확인할 수 없으므로 연락처가 없으면 답변을 전달할 방법이 없다.
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		Inquiry inquiry = Inquiry.create(memberId, request.inquiryType().trim(), request.title().trim(),
				request.content(), phone, email);
		return InquiryResponse.from(inquiryRepository.save(inquiry));
	}

	/** 본인이 로그인 상태로 접수한 문의만 조회된다. */
	public PageResponse<InquiryResponse> getMyInquiries(Long memberId, Pageable pageable) {
		SortValidator.requireAllowed(pageable, Set.of("id", "createdAt"));
		return PageResponse.from(inquiryRepository.findByMemberId(memberId, pageable).map(InquiryResponse::from));
	}
}
