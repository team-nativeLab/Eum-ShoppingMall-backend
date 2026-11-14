package com.example.shopping.domain.product.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.domain.product.dto.request.AdminProductQuestionUpdateRequest;
import com.example.shopping.domain.product.dto.response.AdminProductQuestionResponse;
import com.example.shopping.domain.product.entity.ProductQuestion;
import com.example.shopping.domain.product.entity.ProductQuestionStatus;
import com.example.shopping.domain.product.repository.ProductQuestionRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.validation.SortValidator;

import lombok.RequiredArgsConstructor;

/** 관리자 상품 문의 처리. 비밀 문의도 내용을 모두 보고 답변·메모를 남긴다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProductQuestionService {

	private static final Set<String> SORTABLE = Set.of("id", "createdAt");

	private final ProductQuestionRepository questionRepository;
	private final MemberRepository memberRepository;

	/** 상태(WAITING·ANSWERED)·상품으로 거른 목록 */
	public PageResponse<AdminProductQuestionResponse> search(ProductQuestionStatus status, Long productId,
			Pageable pageable) {
		SortValidator.requireAllowed(pageable, SORTABLE);
		Page<ProductQuestion> questions = questionRepository.search(status, productId, pageable);
		Map<Long, Member> authors = memberRepository.findAllById(questions.map(ProductQuestion::getMemberId).toSet())
				.stream().collect(Collectors.toMap(Member::getId, Function.identity()));
		return PageResponse.from(questions.map(question -> AdminProductQuestionResponse.of(question,
				authors.get(question.getMemberId()))));
	}

	public AdminProductQuestionResponse get(Long questionId) {
		ProductQuestion question = find(questionId);
		return AdminProductQuestionResponse.of(question, memberRepository.findById(question.getMemberId()).orElse(null));
	}

	/** 보낸 항목만 바꾼다. 답변을 남기면 답변 완료가 되고, 빈 답변이면 답변 대기로 되돌린다. */
	@Transactional
	public AdminProductQuestionResponse update(Long adminId, Long questionId,
			AdminProductQuestionUpdateRequest request) {
		ProductQuestion question = find(questionId);
		if (request.answer() != null) {
			question.answer(request.answer().trim(), adminId, LocalDateTime.now());
		}
		if (request.adminMemo() != null) {
			question.changeAdminMemo(request.adminMemo().trim());
		}
		return AdminProductQuestionResponse.of(question, memberRepository.findById(question.getMemberId()).orElse(null));
	}

	private ProductQuestion find(Long questionId) {
		return questionRepository.findById(questionId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_QUESTION_NOT_FOUND));
	}
}
