package com.example.shopping.domain.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 관리자 주문 취소
 *
 * @param reason 취소 사유(200자 이하). 주문 상세의 cancelReason으로 보인다.
 */
public record AdminOrderCancelRequest(@NotBlank @Size(max = 200) String reason) {
}
