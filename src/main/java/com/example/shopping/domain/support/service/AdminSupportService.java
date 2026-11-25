package com.example.shopping.domain.support.service;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.history.entity.StatusHistoryTarget;
import com.example.shopping.domain.history.service.StatusHistoryService;
import com.example.shopping.domain.notification.entity.NotificationMessage;
import com.example.shopping.domain.notification.service.NotificationService;
import com.example.shopping.domain.support.dto.request.AdminInquiryUpdateRequest;
import com.example.shopping.domain.support.dto.response.AdminInquiryDetailResponse;
import com.example.shopping.domain.support.dto.response.AdminInquirySummaryResponse;
import com.example.shopping.domain.support.dto.response.AdminNoticeResponse;
import com.example.shopping.domain.support.dto.request.FaqCreateRequest;
import com.example.shopping.domain.support.dto.response.AdminFaqResponse;
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
	private final InquiryAnswerNotifier inquiryAnswerNotifier;
	private final NotificationService notificationService;
	private final StatusHistoryService statusHistoryService;

	// 공지

	public PageResponse<AdminNoticeResponse> getNotices(Pageable pageable) {
		// 공지는 항상 고정 공지 먼저, 정렬 순서, 최신순으로 내려주므로 다른 정렬 기준은 받지 않는다.
		SortValidator.requireAllowed(pageable, Set.of());
		return PageResponse.from(
				noticeRepository.findAllByOrderByPinnedDescSortOrderAscCreatedAtDesc(pageable).map(AdminNoticeResponse::from));
	}

	@Transactional
	public AdminNoticeResponse createNotice(NoticeCreateRequest request) {
		boolean pinned = request.pinned() != null && request.pinned();
		boolean visible = request.visible() == null || request.visible();
		int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
		Notice notice = Notice.create(request.title().trim(), request.content(), pinned, visible, sortOrder);
		return AdminNoticeResponse.from(noticeRepository.save(notice));
	}

	@Transactional
	public AdminNoticeResponse updateNotice(Long noticeId, NoticeUpdateRequest request) {
		Notice notice = noticeRepository.findById(noticeId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
		notice.update(trim(request.title()), request.content(), request.pinned(), request.visible(),
				request.sortOrder());
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

	/** 숨긴 FAQ까지 정렬 순서대로(#220) */
	public List<AdminFaqResponse> getFaqs() {
		return faqRepository.findAllByOrderBySortOrderAscIdAsc().stream().map(AdminFaqResponse::from).toList();
	}

	@Transactional
	public AdminFaqResponse createFaq(FaqCreateRequest request) {
		int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
		boolean visible = request.visible() == null || request.visible();
		Faq faq = Faq.create(request.category().trim(), request.question().trim(), request.answer(), sortOrder,
				visible);
		return AdminFaqResponse.from(faqRepository.save(faq));
	}

	@Transactional
	public AdminFaqResponse updateFaq(Long faqId, FaqUpdateRequest request) {
		Faq faq = faqRepository.findById(faqId)
				.orElseThrow(() -> new BusinessException(ErrorCode.FAQ_NOT_FOUND));
		faq.update(trim(request.category()), trim(request.question()), request.answer(), request.sortOrder(),
				request.visible());
		return AdminFaqResponse.from(faq);
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
		Inquiry inquiry = findInquiry(inquiryId);
		return AdminInquiryDetailResponse.from(inquiry,
				notificationService.findLatest(InquiryAnswerNotifier.REFERENCE_TYPE, inquiryId).orElse(null),
				statusHistoryService.list(StatusHistoryTarget.INQUIRY, inquiryId));
	}

	/**
	 * 보낸 항목만 바꾼다(#223). 답변을 보내면 답변완료로 바꾸고 고객에게 알림톡을 보낸다. 알림은 답변이 저장된 뒤 따로 보내므로,
	 * 알림이 실패해도 답변 등록은 성공한다. 답변을 빈 문자열로 보내면 답변을 지우고 접수 상태로 되돌린다.
	 * 상태가 바뀌면 이력을 남긴다.
	 */
	@Transactional
	public AdminInquiryDetailResponse updateInquiry(Long inquiryId, AdminInquiryUpdateRequest request) {
		Inquiry inquiry = findInquiry(inquiryId);
		InquiryStatus before = inquiry.getStatus();
		NotificationMessage notification = null;
		if (request.answer() != null) {
			if (StringUtils.hasText(request.answer())) {
				inquiry.answer(request.answer());
				notification = inquiryAnswerNotifier.notifyAnswered(inquiry).orElse(null);
			} else {
				inquiry.clearAnswer();
			}
		}
		if (request.adminMemo() != null) {
			inquiry.changeAdminMemo(request.adminMemo().trim());
		}
		if (inquiry.getStatus() != before) {
			statusHistoryService.record(StatusHistoryTarget.INQUIRY, inquiryId, inquiry.getStatus(),
					inquiry.getStatus() == InquiryStatus.ANSWERED ? "답변 등록" : "답변 취소");
		}
		if (notification == null) {
			notification = notificationService.findLatest(InquiryAnswerNotifier.REFERENCE_TYPE, inquiryId)
					.orElse(null);
		}
		return AdminInquiryDetailResponse.from(inquiry, notification,
				statusHistoryService.list(StatusHistoryTarget.INQUIRY, inquiryId));
	}

	private Inquiry findInquiry(Long inquiryId) {
		return inquiryRepository.findById(inquiryId)
				.orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
	}

	private String trim(String value) {
		return value == null ? null : value.trim();
	}
}
