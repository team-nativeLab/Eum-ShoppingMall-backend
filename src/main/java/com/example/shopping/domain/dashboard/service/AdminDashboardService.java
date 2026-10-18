package com.example.shopping.domain.dashboard.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.dashboard.dto.DashboardPeriod;
import com.example.shopping.domain.dashboard.dto.response.DashboardResponse;
import com.example.shopping.domain.member.entity.MemberRole;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.domain.order.repository.OrderRepository;
import com.example.shopping.domain.quote.entity.QuoteStatus;
import com.example.shopping.domain.quote.repository.QuoteInquiryRepository;
import com.example.shopping.domain.support.entity.InquiryStatus;
import com.example.shopping.domain.support.repository.InquiryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

	private final OrderRepository orderRepository;
	private final MemberRepository memberRepository;
	private final InquiryRepository inquiryRepository;
	private final QuoteInquiryRepository quoteInquiryRepository;

	public DashboardResponse getDashboard(String periodParam) {
		DashboardPeriod period = DashboardPeriod.parse(periodParam);
		LocalDateTime from = period.startFrom(LocalDate.now());
		return new DashboardResponse(period, from,
				orderRepository.countPaidSince(from),
				orderRepository.sumPaidAmountSince(from),
				memberRepository.countByRoleAndCreatedAtGreaterThanEqual(MemberRole.USER, from),
				inquiryRepository.countByStatus(InquiryStatus.RECEIVED),
				quoteInquiryRepository.countByStatus(QuoteStatus.RECEIVED));
	}
}
