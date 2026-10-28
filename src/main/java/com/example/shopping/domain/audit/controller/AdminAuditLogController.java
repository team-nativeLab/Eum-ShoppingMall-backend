package com.example.shopping.domain.audit.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.audit.dto.response.AdminAuditLogResponse;
import com.example.shopping.domain.audit.service.AdminAuditLogService;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.security.CurrentMember;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

	private final AdminAuditLogService service;

	/**
	 * fromDate·toDate는 날짜(2026-10-01)나 날짜·시간(2026-10-01T09:00:00)으로 받는다.
	 * 날짜만 주면 fromDate는 그날 0시부터, toDate는 그날이 끝날 때까지로 본다.
	 */
	@GetMapping
	public ApiResponse<PageResponse<AdminAuditLogResponse>> search(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) Long actorMemberId,
			@RequestParam(required = false) String targetType,
			@RequestParam(required = false) String fromDate,
			@RequestParam(required = false) String toDate,
			@PageableDefault(size = 20) Pageable pageable) {
		return ApiResponse.ok(service.search(CurrentMember.id(jwt), actorMemberId, targetType,
				parseDate(fromDate, false), parseDate(toDate, true), pageable));
	}

	private static LocalDateTime parseDate(String value, boolean endOfRange) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			if (value.length() == "yyyy-MM-dd".length()) {
				LocalDate date = LocalDate.parse(value);
				return endOfRange ? date.plusDays(1).atStartOfDay() : date.atStartOfDay();
			}
			return LocalDateTime.parse(value);
		} catch (DateTimeParseException e) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}
}
