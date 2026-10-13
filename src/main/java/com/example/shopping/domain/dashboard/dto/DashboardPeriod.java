package com.example.shopping.domain.dashboard.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

/** 대시보드 집계 기간. 오늘을 포함해 거슬러 올라간다 (WEEK = 최근 7일, MONTH = 최근 30일). */
public enum DashboardPeriod {
	TODAY(0), WEEK(6), MONTH(29);

	private final int daysBack;

	DashboardPeriod(int daysBack) {
		this.daysBack = daysBack;
	}

	public LocalDateTime startFrom(LocalDate today) {
		return today.minusDays(daysBack).atStartOfDay();
	}

	/** 대소문자를 가리지 않는다. 값이 없으면 WEEK. */
	public static DashboardPeriod parse(String value) {
		if (value == null || value.isBlank()) {
			return WEEK;
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}
}
