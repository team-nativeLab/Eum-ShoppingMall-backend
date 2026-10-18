package com.example.shopping.domain.audit.controller;

import java.time.LocalDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.audit.dto.response.AdminAuditLogResponse;
import com.example.shopping.domain.audit.service.AdminAuditLogService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.security.CurrentMember;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

	private final AdminAuditLogService service;

	@GetMapping
	public ApiResponse<PageResponse<AdminAuditLogResponse>> search(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) Long actorMemberId,
			@RequestParam(required = false) String targetType,
			@RequestParam(required = false) LocalDateTime fromDate,
			@RequestParam(required = false) LocalDateTime toDate,
			@PageableDefault(size = 20) Pageable pageable) {
		return ApiResponse.ok(service.search(CurrentMember.id(jwt), actorMemberId, targetType, fromDate, toDate, pageable));
	}
}
