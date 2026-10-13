package com.example.shopping.domain.member.service;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.member.dto.AdminMemberDetailResponse;
import com.example.shopping.domain.member.dto.AdminMemberSummaryResponse;
import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.repository.OrderRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.validation.SortValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberService {

	private static final int RECENT_ORDER_COUNT = 20;
	private static final Set<String> SORTABLE = Set.of("id", "email", "name", "createdAt");

	private final MemberRepository memberRepository;
	private final OrderRepository orderRepository;

	public PageResponse<AdminMemberSummaryResponse> search(String keyword, Pageable pageable) {
		SortValidator.requireAllowed(pageable, SORTABLE);
		String trimmed = StringUtils.hasText(keyword) ? keyword.trim() : null;
		return PageResponse.from(memberRepository.search(trimmed, pageable).map(AdminMemberSummaryResponse::from));
	}

	public AdminMemberDetailResponse getDetail(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		List<Order> recentOrders = orderRepository.findByMemberId(memberId,
				PageRequest.of(0, RECENT_ORDER_COUNT, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
		return AdminMemberDetailResponse.of(member, recentOrders);
	}
}
