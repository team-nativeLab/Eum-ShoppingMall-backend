package com.example.shopping.domain.product.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.domain.product.dto.request.AdminProductReviewUpdateRequest;
import com.example.shopping.domain.product.dto.response.AdminProductReviewResponse;
import com.example.shopping.domain.product.entity.ProductReview;
import com.example.shopping.domain.product.repository.ProductRepository;
import com.example.shopping.domain.product.repository.ProductReviewRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.validation.SortValidator;

import lombok.RequiredArgsConstructor;

/** 관리자 리뷰 관리: 숨기기·다시 보이기, 답변, 메모. 숨기면 상품의 리뷰 수·평점에서 뺀다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProductReviewService {

	private static final Set<String> SORTABLE = Set.of("id", "createdAt", "rating");

	private final ProductReviewRepository reviewRepository;
	private final ProductRepository productRepository;
	private final MemberRepository memberRepository;

	public PageResponse<AdminProductReviewResponse> search(Long productId, Boolean visible, Integer rating,
			Pageable pageable) {
		SortValidator.requireAllowed(pageable, SORTABLE);
		Page<ProductReview> reviews = reviewRepository.search(productId, visible, rating, pageable);
		Map<Long, Member> authors = AuthorNames.load(memberRepository, reviews.map(ProductReview::getMemberId).toSet());
		return PageResponse.from(reviews.map(review -> AdminProductReviewResponse.of(review,
				authors.get(review.getMemberId()))));
	}

	/** 보낸 항목만 바꾼다. 보임 여부가 바뀌면 상품의 리뷰 수·별점 합계도 바꾼다. */
	@Transactional
	public AdminProductReviewResponse update(Long reviewId, AdminProductReviewUpdateRequest request) {
		ProductReview review = reviewRepository.findById(reviewId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
		boolean visibilityChanged = request.visible() != null && review.changeVisible(request.visible());
		if (request.adminReply() != null) {
			review.reply(request.adminReply().trim(), LocalDateTime.now());
		}
		if (request.adminMemo() != null) {
			review.changeAdminMemo(request.adminMemo().trim());
		}
		AdminProductReviewResponse response = AdminProductReviewResponse.of(review,
				memberRepository.findById(review.getMemberId()).orElse(null));
		if (visibilityChanged) {
			// 이 쿼리는 영속성 컨텍스트를 비우므로 응답을 먼저 만든다.
			int sign = review.isVisible() ? 1 : -1;
			productRepository.adjustReviewStats(review.getProduct().getId(), sign, sign * review.getRating());
		}
		return response;
	}
}
