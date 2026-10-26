package com.example.shopping.domain.support.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.domain.notification.entity.NotificationMessage;
import com.example.shopping.domain.notification.service.NotificationService;
import com.example.shopping.domain.support.entity.Inquiry;

import lombok.extern.slf4j.Slf4j;

/**
 * 문의에 답변을 등록하면 카카오톡 채널 알림톡으로 알린다. 받는 번호는 접수 때 받은 연락처, 없으면 회원 정보의 휴대폰 번호다.
 * 비회원은 답변을 볼 화면이 없으므로 답변 내용을 알림에 함께 싣는다.
 * 대행사 콘솔에 등록할 템플릿 예: "#{문의제목} 문의에 답변이 등록되었습니다.\n\n#{답변}"
 */
@Slf4j
@Component
public class InquiryAnswerNotifier {

	public static final String REFERENCE_TYPE = "INQUIRY_ANSWER";
	// 알림톡 본문은 1,000자까지라 템플릿 문구 자리를 남기고 답변을 자른다.
	static final int MAX_ANSWER_LENGTH = 700;
	private static final Pattern MOBILE = Pattern.compile("^01[016789]\\d{7,8}$");

	private final NotificationService notificationService;
	private final MemberRepository memberRepository;
	private final String templateCode;

	public InquiryAnswerNotifier(NotificationService notificationService, MemberRepository memberRepository,
			@Value("${app.notification.alimtalk.inquiry-answer-template:INQUIRY_ANSWER}") String templateCode) {
		this.notificationService = notificationService;
		this.memberRepository = memberRepository;
		this.templateCode = templateCode;
	}

	/** 답변을 저장하는 트랜잭션 안에서 부른다. 알림톡은 휴대폰 번호로만 보낼 수 있어, 번호가 없으면 보내지 않는다. */
	public Optional<NotificationMessage> notifyAnswered(Inquiry inquiry) {
		String phone = recipientPhone(inquiry);
		if (phone == null) {
			log.info("문의 {} 답변 알림을 보내지 않습니다: 휴대폰 번호가 없습니다.", inquiry.getId());
			return Optional.empty();
		}
		Map<String, String> variables = new LinkedHashMap<>();
		variables.put("문의제목", inquiry.getTitle());
		variables.put("답변", shorten(inquiry.getAnswer()));
		return Optional.of(notificationService.enqueueAlimtalk(phone, templateCode, variables, REFERENCE_TYPE,
				inquiry.getId()));
	}

	private String recipientPhone(Inquiry inquiry) {
		String phone = digitsOnly(inquiry.getContactPhone());
		if (phone == null && inquiry.getMemberId() != null) {
			phone = memberRepository.findById(inquiry.getMemberId()).map(Member::getPhone).map(this::digitsOnly)
					.orElse(null);
		}
		return phone != null && MOBILE.matcher(phone).matches() ? phone : null;
	}

	private String digitsOnly(String phone) {
		return StringUtils.hasText(phone) ? phone.replaceAll("\\D", "") : null;
	}

	private String shorten(String answer) {
		if (answer == null || answer.length() <= MAX_ANSWER_LENGTH) {
			return answer;
		}
		return answer.substring(0, MAX_ANSWER_LENGTH) + "...(전체 답변은 고객센터로 문의해 주세요)";
	}
}
