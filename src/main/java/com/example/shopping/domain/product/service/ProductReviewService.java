package com.example.shopping.domain.product.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.domain.order.entity.OrderItem;
import com.example.shopping.domain.order.entity.OrderItemStatus;
import com.example.shopping.domain.order.entity.OrderStatus;
import com.example.shopping.domain.product.dto.request.ProductReviewCreateRequest;
import com.example.shopping.domain.product.dto.response.ProductReviewResponse;
import com.example.shopping.domain.product.dto.response.ProductReviewSummaryResponse;
import com.example.shopping.domain.product.dto.response.ReviewableItemResponse;
import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductReview;
import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.domain.product.repository.ProductRepository;
import com.example.shopping.domain.product.repository.ProductReviewRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * 상품 리뷰. 누구나 보고, 배송이 끝난 주문의 상품마다 회원이 하나씩 쓴다.
 * 상품의 리뷰 수·별점 합계(Product.reviewCount·ratingSum)를 함께 바꿔 목록에서 리뷰를 세지 않게 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductReviewService {

	static final int MAX_PAGE_SIZE = 50;
	static final int MAX_REVIEWABLE_ITEMS = 100;

	private final ProductReviewRepository reviewRepository;
	private final ProductRepository productRepository;
	private final MemberRepository memberRepository;

	/**
	 * 보이는 리뷰 목록. 작성자 이름은 가린다.
	 *
	 * @param sort     latest(기본) | rating_high | rating_low
	 * @param viewerId 로그인한 회원. 비회원이면 null
	 */
	public PageResponse<ProductReviewResponse> list(Long productId, String sort, Long viewerId, Pageable pageable) {
		visibleProduct(productId);
		Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
				ReviewSort.from(sort).sort);
		Page<ProductReview> reviews = reviewRepository.findByProductIdAndVisibleTrue(productId, page);
		Map<Long, Member> authors = AuthorNames.load(memberRepository, reviews.map(ProductReview::getMemberId).toSet());
		return PageResponse.from(reviews.map(review -> ProductReviewResponse.of(review,
				AuthorNames.masked(authors.get(review.getMemberId())), viewerId)));
	}

	/** 보이는 리뷰의 평균 별점과 별점별 수 */
	public ProductReviewSummaryResponse summary(Long productId) {
		visibleProduct(productId);
		Map<Integer, Long> counts = new LinkedHashMap<>();
		for (int rating = ProductReview.MIN_RATING; rating <= ProductReview.MAX_RATING; rating++) {
			counts.put(rating, 0L);
		}
		for (Object[] row : reviewRepository.countVisibleByRating(productId)) {
			counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
		}
		long total = counts.values().stream().mapToLong(Long::longValue).sum();
		long ratingSum = counts.entrySet().stream().mapToLong(entry -> entry.getKey() * entry.getValue()).sum();
		Double average = total == 0 ? null : Math.round(ratingSum * 10.0 / total) / 10.0;
		return new ProductReviewSummaryResponse(productId, average, total, counts, 0);
	}

	/**
	 * 회원 본인의 주문 품목 중 배송이 끝났고 취소·반품되지 않은 것에만 쓴다. 품목마다 하나다(이미 썼으면 409).
	 * 남의 품목이거나 다른 상품의 품목이면 쓸 수 없는 품목과 똑같이 답한다.
	 */
	@Transactional
	public ProductReviewResponse create(Long productId, Long memberId, ProductReviewCreateRequest request) {
		Product product = visibleProduct(productId);
		Member author = memberRepository.findById(memberId)
				.filter(member -> member.getStatus() == MemberStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		OrderItem item = reviewRepository.findOrderItemOfMember(request.orderItemId(), memberId)
				.filter(orderItem -> orderItem.getOrder().getStatus() == OrderStatus.DELIVERED)
				.filter(orderItem -> orderItem.getStatus() == OrderItemStatus.ORDERED)
				.filter(orderItem -> orderItem.getSku().getProduct().getId().equals(productId))
				.orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED));
		if (reviewRepository.existsByOrderItemId(item.getId())) {
			throw new BusinessException(ErrorCode.REVIEW_ALREADY_WRITTEN);
		}
		ProductReview review;
		try {
			review = reviewRepository.saveAndFlush(ProductReview.create(product, memberId, item.getId(),
					request.rating(), request.content().trim(), item.getOptionName()));
		} catch (DataIntegrityViolationException e) {
			// 같은 품목으로 동시에 들어온 요청은 유니크 제약이 막는다.
			throw new BusinessException(ErrorCode.REVIEW_ALREADY_WRITTEN);
		}
		ProductReviewResponse response = ProductReviewResponse.of(review, AuthorNames.masked(author), memberId);
		productRepository.adjustReviewStats(productId, 1, review.getRating());
		return response;
	}

	/** 마이페이지에서 리뷰를 쓸 수 있는 주문 상품. 고객에게 보이는 상품만, 최근 주문 순으로 100개까지 */
	public List<ReviewableItemResponse> reviewableItems(Long memberId) {
		return reviewRepository.findReviewableItems(memberId, OrderStatus.DELIVERED, OrderItemStatus.ORDERED,
						PageRequest.of(0, MAX_REVIEWABLE_ITEMS)).stream()
				.filter(item -> ProductStatus.CUSTOMER_VISIBLE.contains(item.getSku().getProduct().getStatus()))
				.map(ReviewableItemResponse::from)
				.toList();
	}

	private Product visibleProduct(Long productId) {
		return productRepository.findById(productId)
				.filter(product -> ProductStatus.CUSTOMER_VISIBLE.contains(product.getStatus()))
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
	}

	enum ReviewSort {
		LATEST(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
		RATING_HIGH(Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
		RATING_LOW(Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

		private final Sort sort;

		ReviewSort(Sort sort) {
			this.sort = sort;
		}

		static ReviewSort from(String value) {
			if (value == null || value.isBlank()) {
				return LATEST;
			}
			try {
				return valueOf(value.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				throw new BusinessException(ErrorCode.INVALID_INPUT);
			}
		}
	}
}
