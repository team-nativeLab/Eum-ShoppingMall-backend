package com.example.shopping.domain.member.dto.response;

/**
 * OTP 앱에 등록할 값. 화면은 otpauthUri를 QR 코드로 보여 주고, QR을 못 읽는 경우를 위해 secret도 보여 준다.
 * 등록을 마치기 전까지만 받을 수 있고, 다시 요청하면 새 값으로 바뀐다.
 */
public record AdminMfaSetupResponse(String secret, String otpauthUri) {
}
