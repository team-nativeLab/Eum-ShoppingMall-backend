package com.example.shopping.domain.support.service;

import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.support.dto.response.AdminInquiryDetailResponse;
import com.example.shopping.domain.support.dto.response.AdminInquirySummaryResponse;
import com.example.shopping.domain.support.dto.response.AdminNoticeResponse;
import com.example.shopping.domain.support.dto.request.FaqCreateRequest;
import com.example.shopping.domain.support.dto.response.FaqResponse;
import com.example.shopping.domain.support.dto.request.FaqUpdateRequest;
import com.example.shopping.domain.support.dto.request.NoticeCreateRequest;
import com.example.shopping.domain.support.dto.request.NoticeUpdateRequest;
import com.example.shopping.domain.support.entity.Faq;
import com.example.shopping.domain.support.entity.Inquiry;
import com.example.shopping.domain.support.entity.InquiryStatus;
import com.example.shopping.domain.support.entity.Notice;
import com.example.shopping.domain.support.repository.FaqRepository;
import com.example.shopping.domain.support.repository.InquiryRepository;
import com.example.shopping.domain.support.repository.NoticeRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.validation.SortValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSupportService {

	private final NoticeRepository noticeRepository;
	private final FaqRepository faqRepository;
	private final InquiryRepository inquiryRepository;

	// 공지

	public PageResponse<AdminNoticeResponse> getNotices(Pageable pageable) {
		// 공지는 항상 고정 공지 먼저, 최신순으로 내려주므로 다른 정렬 기준은 받지 않는다.
		SortValidator.requireAllowed(pageable, Set.of());
		return PageResponse.from(
				noticeRepository.findAllByOrderByPinnedDescCreatedAtDesc(pageable).map(AdminNoticeResponse::from));
	}

	@Transactional
	public AdminNoticeResponse createNotice(NoticeCreateRequest request) {
		boolean pinned = request.pinned() != null && request.pinned();
		boolean visible = request.visible() == null || request.visible();
		Notice notice = Notice.create(request.title().trim(), request.content(), pinned, visible);
		return AdminNoticeResponse.from(noticeRepository.save(notice));
	}

	@Transactional
	public AdminNoticeResponse updateNotice(Long noticeId, NoticeUpdateRequest request) {
		Notice notice = noticeRepository.findById(noticeId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
		notice.update(trim(request.title()), request.content(), request.pinned(), request.visible());
		// 갱신된 수정 시각을 응답에 싣기 위해 먼저 DB에 반영한다.
		return AdminNoticeResponse.from(noticeRepository.saveAndFlush(notice));
	}

	@Transactional
	public void deleteNotice(Long noticeId) {
		if (!noticeRepository.existsById(noticeId)) {
			throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
		}
		noticeRepository.deleteById(noticeId);
	}

	// FAQ

	@Transactional
	public FaqResponse createFaq(FaqCreateRequest request) {
		int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
		Faq faq = Faq.create(request.category().trim(), request.question().trim(), request.answer(), sortOrder);
		return FaqResponse.from(faqRepository.save(faq));
	}

	@Transactional
	public FaqResponse updateFaq(Long faqId, FaqUpdateRequest request) {
		Faq faq = faqRepository.findById(faqId)
				.orElseThrow(() -> new BusinessException(ErrorCode.FAQ_NOT_FOUND));
		faq.update(trim(request.category()), trim(request.question()), request.answer(), request.sortOrder());
		return FaqResponse.from(faq);
	}

	@Transactional
	public void deleteFaq(Long faqId) {
		if (!faqRepository.existsById(faqId)) {
			throw new BusinessException(ErrorCode.FAQ_NOT_FOUND);
		}
		faqRepository.deleteById(faqId);
	}

	// 1:1 문의

	/** 상태를 지정하지 않으면 전체를 조회한다. */
	public PageResponse<AdminInquirySummaryResponse> getInquiries(InquiryStatus status, Pageable pageable) {
		SortValidator.requireAllowed(pageable, Set.of("id", "createdAt"));
		var page = status == null ? inquiryRepository.findAll(pageable)
				: inquiryRepository.findByStatus(status, pageable);
		return PageResponse.from(page.map(AdminInquirySummaryResponse::from));
	}

	public AdminInquiryDetailResponse getInquiry(Long inquiryId) {
		return AdminInquiryDetailResponse.from(findInquiry(inquiryId));
	}

	@Transactional
	public AdminInquiryDetailResponse answerInquiry(Long inquiryId, String answer) {
		Inquiry inquiry = findInquiry(inquiryId);
		inquiry.answer(answer);
		return AdminInquiryDetailResponse.from(inquiry);
	}

	private Inquiry findInquiry(Long inquiryId) {
		return inquiryRepository.findById(inquiryId)
				.orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
	}

	private String trim(String value) {
		return value == null ? null : value.trim();
	}
}
