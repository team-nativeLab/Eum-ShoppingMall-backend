package com.example.shopping.domain.audit.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.audit.dto.AdminAuditLogResponse;
import com.example.shopping.domain.audit.entity.AdminAuditLog;
import com.example.shopping.domain.audit.repository.AdminAuditLogRepository;
import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberRole;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.validation.SortValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

	private static final java.util.Set<String> SORTABLE = java.util.Set.of("id", "createdAt");

	private final AdminAuditLogRepository repository;
	private final MemberRepository memberRepository;

	@Transactional
	public void record(Long actorMemberId, String ip, String action, String targetType, String targetId,
			String summary, int responseStatus) {
		repository.save(AdminAuditLog.create(actorMemberId, ip, action, targetType, targetId, summary, responseStatus));
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminAuditLogResponse> search(Long actorMemberId, Long filterActorId, String targetType,
			LocalDateTime fromDate, LocalDateTime toDate, Pageable pageable) {
		Member actor = memberRepository.findById(actorMemberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (actor.getRole() != MemberRole.SUPER_ADMIN) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		SortValidator.requireAllowed(pageable, SORTABLE);
		String type = StringUtils.hasText(targetType) ? targetType.trim() : null;
		if (fromDate != null && toDate != null && !fromDate.isBefore(toDate)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return PageResponse.from(repository.search(filterActorId, type, fromDate, toDate, pageable)
				.map(AdminAuditLogResponse::from));
	}
}
