package com.example.shopping.domain.member.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberRole;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderStatus;

/**
 * recentOrders는 최근 주문 20건
 *
 * @param id               회원 ID
 * @param email            이메일
 * @param name             이름
 * @param phone            연락처. 없으면 null
 * @param role             권한. USER(회원), ADMIN(관리자)
 * @param status           상태. ACTIVE(정상), SUSPENDED(정지), WITHDRAWN(탈퇴)
 * @param createdAt        가입 시각
 * @param withdrawnAt      탈퇴 시각. 탈퇴하지 않았으면 null
 * @param suspensionReason 정지 사유. 정지가 아니면 null
 * @param recentOrders     최근 주문 20건. 최근 것부터
 */
public record AdminMemberDetailResponse(Long id, String email, String name, String phone, MemberRole role,
		MemberStatus status, LocalDateTime createdAt, LocalDateTime withdrawnAt, String suspensionReason,
		List<OrderSummary> recentOrders) {

	/**
	 * 회원 상세의 주문 한 건
	 *
	 * @param id          주문 ID
	 * @param orderNo     주문번호
	 * @param status      주문 상태. PENDING(결제 전), PAYMENT_PROCESSING(결제 확인 중), WAITING_DEPOSIT(입금 대기), PAID(결제 완료),
	 *                    PREPARING(상품 준비중), CANCEL_REQUESTED(취소 처리 중), SHIPPED(배송중), DELIVERED(배송완료), CANCELED(취소),
	 *                    EXPIRED(결제 기한 만료)
	 * @param orderName   주문명(예: 안전모 외 2건)
	 * @param totalAmount 결제 금액(원)
	 * @param createdAt   주문 시각
	 */
	public record OrderSummary(Long id, String orderNo, OrderStatus status, String orderName, long totalAmount,
			LocalDateTime createdAt) {

		static OrderSummary from(Order order) {
			return new OrderSummary(order.getId(), order.getOrderNo(), order.getStatus(), order.getOrderName(),
					order.getTotalAmount(), order.getCreatedAt());
		}
	}

	public static AdminMemberDetailResponse of(Member member, List<Order> recentOrders) {
		return new AdminMemberDetailResponse(member.getId(), member.getEmail(), member.getName(), member.getPhone(),
				member.getRole(), member.getStatus(), member.getCreatedAt(), member.getWithdrawnAt(),
				member.getSuspensionReason(),
				recentOrders.stream().map(OrderSummary::from).toList());
	}
}
