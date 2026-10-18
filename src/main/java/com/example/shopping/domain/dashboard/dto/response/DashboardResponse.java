package com.example.shopping.domain.dashboard.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.dashboard.dto.DashboardPeriod;

/**
 * @param from                집계 시작 시각 (이때부터 지금까지)
 * @param paidOrderCount      기간 내에 결제된 주문 수 (전체 취소 주문 제외)
 * @param salesAmount         위 주문들의 결제 금액 합계(원)
 * @param newMemberCount      기간 내에 가입한 회원 수 (관리자 계정 제외)
 * @param pendingInquiryCount 답변을 기다리는 1:1 문의 수 (기간과 무관한 현재 값)
 * @param pendingQuoteCount   접수 상태로 남아 있는 견적 문의 수 (기간과 무관한 현재 값)
 */
public record DashboardResponse(DashboardPeriod period, LocalDateTime from, long paidOrderCount, long salesAmount,
		long newMemberCount, long pendingInquiryCount, long pendingQuoteCount) {
}
