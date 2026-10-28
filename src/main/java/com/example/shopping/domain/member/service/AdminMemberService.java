package com.example.shopping.domain.member.service;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.member.dto.response.AdminMemberDetailResponse;
import com.example.shopping.domain.member.dto.response.AdminMemberSummaryResponse;
import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberRole;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.domain.member.dto.request.AdminMemberStatusUpdateRequest;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.repository.OrderRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.validation.SortValidator;
import com.example.shopping.global.security.MemberTokenStateCache;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberService {

	private static final int RECENT_ORDER_COUNT = 20;
	private static final Set<String> SORTABLE = Set.of("id", "email", "name", "createdAt");

	private final MemberRepository memberRepository;
	private final OrderRepository orderRepository;
	private final RefreshTokenService refreshTokenService;
	private final MemberTokenStateCache memberTokenStateCache;

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

	@Transactional
	public AdminMemberDetailResponse changeRole(Long actorId, Long targetId, String requestedRole) {
		Member actor = memberRepository.findById(actorId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (actor.getRole() != MemberRole.SUPER_ADMIN) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		if (actorId.equals(targetId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		Member target = memberRepository.findById(targetId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (target.getRole() == MemberRole.SUPER_ADMIN) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		if (target.getStatus() == MemberStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		MemberRole role = MemberRole.valueOf(requestedRole);
		if (role == MemberRole.ADMIN && target.getStatus() != MemberStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
		}
		if (target.getRole() != role) {
			target.changeRole(role);
			refreshTokenService.revokeAll(targetId);
			memberTokenStateCache.refresh(target);
		}
		List<Order> recentOrders = orderRepository.findByMemberId(targetId,
				PageRequest.of(0, RECENT_ORDER_COUNT, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
		return AdminMemberDetailResponse.of(target, recentOrders);
	}

	@Transactional
	public AdminMemberDetailResponse changeStatus(Long actorId, Long targetId, AdminMemberStatusUpdateRequest request) {
		Member actor = memberRepository.findById(actorId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (actor.getRole() != MemberRole.ADMIN && actor.getRole() != MemberRole.SUPER_ADMIN) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		if (actorId.equals(targetId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		Member target = memberRepository.findById(targetId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (target.getRole() == MemberRole.SUPER_ADMIN || target.getStatus() == MemberStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		MemberStatus status = MemberStatus.valueOf(request.status());
		if (status == MemberStatus.SUSPENDED && !StringUtils.hasText(request.reason())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		if (target.getStatus() != status) {
			target.changeStatus(status, request.reason() == null ? null : request.reason().trim());
			memberTokenStateCache.refresh(target);
			if (status == MemberStatus.SUSPENDED) {
				refreshTokenService.revokeAll(targetId);
			}
		}
		List<Order> recentOrders = orderRepository.findByMemberId(targetId,
				PageRequest.of(0, RECENT_ORDER_COUNT, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
		return AdminMemberDetailResponse.of(target, recentOrders);
	}
}
