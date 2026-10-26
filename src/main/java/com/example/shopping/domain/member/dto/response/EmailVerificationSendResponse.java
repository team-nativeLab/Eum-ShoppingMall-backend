package com.example.shopping.domain.member.dto.response;

import java.time.LocalDateTime;

/** @param expiresAt 인증번호를 입력할 수 있는 마지막 시각 */
public record EmailVerificationSendResponse(LocalDateTime expiresAt) {
}
