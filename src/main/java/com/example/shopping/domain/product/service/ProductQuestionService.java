package com.example.shopping.domain.product.service;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.domain.product.dto.request.ProductQuestionCreateRequest;
import com.example.shopping.domain.product.dto.response.ProductQuestionResponse;
import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductQuestion;
import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.domain.product.repository.ProductQuestionRepository;
import com.example.shopping.domain.product.repository.ProductRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.PageResponse;

import lombok.RequiredArgsConstructor;

/** 상품 상세의 상품 문의(Q&A). 누구나 목록을 보고, 회원만 문의를 남긴다. 고객에게 보이는 상품에만 남길 수 있다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQuestionService {

	static final int MAX_PAGE_SIZE = 50;

	private final ProductQuestionRepository questionRepository;
	private final ProductRepository productRepository;
	private final MemberRepository memberRepository;

	/**
	 * 최근 순 목록. 작성자 이름은 가리고, 비밀 문의는 작성자 본인이 아니면 내용과 답변을 비운다.
	 *
	 * @param viewerId 로그인한 회원. 비회원이면 null
	 */
	public PageResponse<ProductQuestionResponse> list(Long productId, Long viewerId, Pageable pageable) {
		visibleProduct(productId);
		Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
		Page<ProductQuestion> questions = questionRepository.findByProductId(productId, page);
		Map<Long, Member> authors = AuthorNames.load(memberRepository,
				questions.map(ProductQuestion::getMemberId).toSet());
		return PageResponse.from(questions.map(question -> ProductQuestionResponse.of(question,
				AuthorNames.masked(authors.get(question.getMemberId())), viewerId)));
	}

	@Transactional
	public ProductQuestionResponse create(Long productId, Long memberId, ProductQuestionCreateRequest request) {
		Product product = visibleProduct(productId);
		Member author = memberRepository.findById(memberId)
				.filter(member -> member.getStatus() == MemberStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		ProductQuestion question = questionRepository.save(ProductQuestion.create(product, memberId,
				request.title().trim(), request.content().trim(), Boolean.TRUE.equals(request.isPrivate())));
		return ProductQuestionResponse.of(question, AuthorNames.masked(author), memberId);
	}

	private Product visibleProduct(Long productId) {
		return productRepository.findById(productId)
				.filter(product -> ProductStatus.CUSTOMER_VISIBLE.contains(product.getStatus()))
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
	}
}
