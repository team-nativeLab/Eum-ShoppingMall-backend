package com.example.shopping.domain.dashboard.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.dashboard.dto.DashboardResponse;
import com.example.shopping.domain.dashboard.service.AdminDashboardService;
import com.example.shopping.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

	private final AdminDashboardService adminDashboardService;

	/** ADM-002 운영 현황 집계 (?period=today|week|month, 생략하면 week) */
	@GetMapping
	public ApiResponse<DashboardResponse> dashboard(@RequestParam(required = false) String period) {
		return ApiResponse.ok(adminDashboardService.getDashboard(period));
	}
}
