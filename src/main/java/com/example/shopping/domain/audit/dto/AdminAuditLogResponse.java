package com.example.shopping.domain.audit.dto;

import java.time.LocalDateTime;

import com.example.shopping.domain.audit.entity.AdminAuditLog;

public record AdminAuditLogResponse(Long id, Long actorMemberId, LocalDateTime createdAt, String ipAddress,
		String action, String targetType, String targetId, String summary, int responseStatus) {

	public static AdminAuditLogResponse from(AdminAuditLog log) {
		return new AdminAuditLogResponse(log.getId(), log.getActorMemberId(), log.getCreatedAt(), log.getIpAddress(),
				log.getAction(), log.getTargetType(), log.getTargetId(), log.getSummary(), log.getResponseStatus());
	}
}
