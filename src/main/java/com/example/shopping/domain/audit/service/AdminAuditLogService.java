package com.example.shopping.domain.audit.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.audit.dto.response.AdminAuditLogResponse;
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
			String summary, String details, int responseStatus) {
		repository.save(AdminAuditLog.create(actorMemberId, ip, action, targetType, targetId, summary, details,
				responseStatus));
	}

	/**
	 * 관리자 로그인 성공·실패를 남긴다. 로그인(AuthService)은 트랜잭션 밖에서 돌므로 이 기록은 따로 커밋된다.
	 *
	 * @param memberId 로그인한 관리자. 실패면 null
	 * @param email    실패한 경우 입력한 이메일 (어느 계정을 노렸는지 확인용)
	 */
	@Transactional
	public void recordAdminLogin(Long memberId, String ip, String email, int responseStatus) {
		boolean succeeded = responseStatus >= 200 && responseStatus < 300;
		String details = succeeded ? null : AuditDetails.ofEmail(email);
		repository.save(AdminAuditLog.create(memberId, ip, "LOGIN", "auth", null,
				succeeded ? "관리자 로그인 성공" : "관리자 로그인 실패", details, responseStatus));
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
