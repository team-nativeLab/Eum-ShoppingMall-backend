package com.example.shopping.domain.member.dto.response;

import java.time.LocalDateTime;

/**
 * @param verificationToken 회원가입 요청의 emailVerificationToken에 그대로 넣는다. 한 번만 쓸 수 있다.
 * @param expiresAt         이 시각까지 가입해야 한다.
 */
public record EmailVerificationConfirmResponse(String verificationToken, LocalDateTime expiresAt) {
}
