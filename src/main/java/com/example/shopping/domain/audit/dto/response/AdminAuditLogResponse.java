package com.example.shopping.domain.audit.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.audit.entity.AdminAuditLog;

/**
 * 관리자 감사 로그 한 건. 관리자의 변경 요청, 개인정보 조회, 관리자 로그인을 남긴다.
 *
 * @param id             기록 ID
 * @param actorMemberId  요청한 관리자의 회원 ID. 관리자 로그인 실패처럼 누가 했는지 모르면 null
 * @param createdAt      기록 시각
 * @param ipAddress      요청한 IP
 * @param action         HTTP 메서드(GET, POST, PUT, PATCH, DELETE). 관리자 로그인은 LOGIN
 * @param targetType     대상 종류. 경로에서 ID가 붙은 가장 안쪽 이름(예: products, skus). 관리자 로그인은 auth
 * @param targetId       대상 ID(경로의 ID). 없으면 null
 * @param summary        요약. 보통 메서드와 경로(예: PATCH /api/v1/admin/orders/1), 관리자 로그인은 결과 안내
 * @param details        바꾼 항목과 값(요청 본문)이나 조회 조건. 비밀번호 같은 민감한 값은 가려서 남긴다.
 * @param responseStatus 응답 HTTP 상태 코드
 */
public record AdminAuditLogResponse(Long id, Long actorMemberId, LocalDateTime createdAt, String ipAddress,
		String action, String targetType, String targetId, String summary, String details, int responseStatus) {

	public static AdminAuditLogResponse from(AdminAuditLog log) {
		return new AdminAuditLogResponse(
				log.getId(),
				log.getActorMemberId(),
				log.getCreatedAt(),
				log.getIpAddress(),
				log.getAction(),
				log.getTargetType(),
				log.getTargetId(),
				log.getSummary(),
				log.getDetails(),
				log.getResponseStatus());
	}
}
