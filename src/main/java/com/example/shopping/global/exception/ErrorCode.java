package com.example.shopping.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 프론트와 공유하는 에러 코드. 이름(code)은 배포 후 바꾸지 않는다. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 공통
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이나 회원가입을 해주세요."),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
	TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 많습니다. 잠시 후 다시 시도해 주세요."),
	CONCURRENT_UPDATE(HttpStatus.CONFLICT, "다른 요청이 먼저 처리되었습니다. 새로고침 후 다시 시도해 주세요."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
	FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "파일이 너무 큽니다. 5MB 이하의 파일만 올릴 수 있습니다."),
	REQUEST_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "요청 내용이 너무 큽니다."),
	INVALID_FILE(HttpStatus.BAD_REQUEST, "업로드할 수 없는 파일입니다. JPG, PNG, GIF, WEBP 이미지만 올릴 수 있습니다."),
	BANNER_NOT_FOUND(HttpStatus.NOT_FOUND, "배너를 찾을 수 없습니다."),
	CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "콘텐츠를 찾을 수 없습니다."),

	// 회원
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
	LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 많아 잠시 제한되었습니다. 10분 후 다시 시도해 주세요."),
	ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 제한된 계정입니다."),
	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
	CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),
	PASSWORD_NOT_CHANGED(HttpStatus.BAD_REQUEST, "새 비밀번호가 현재 비밀번호와 같습니다."),

	// 상품
	PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
	PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT, "판매 중인 상품이 아닙니다."),
	OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
	SKU_NOT_FOUND(HttpStatus.NOT_FOUND, "상품 옵션을 찾을 수 없습니다."),
	STOCK_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "재고는 옵션당 10억 개를 넘을 수 없습니다."),
	CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
	CATEGORY_NOT_EMPTY(HttpStatus.CONFLICT, "하위 카테고리나 소속 상품이 있어 삭제할 수 없습니다."),

	// 장바구니
	CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니에 없는 상품입니다."),
	CART_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "장바구니에는 상품 50종, 상품당 99개까지 담을 수 있습니다."),

	// 주문
	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
	ORDER_EXPIRED(HttpStatus.GONE, "주문이 만료되었습니다."),
	FORBIDDEN_ORDER(HttpStatus.FORBIDDEN, "본인의 주문이 아닙니다."),
	INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "현재 주문 상태에서는 처리할 수 없습니다."),
	DELIVERY_REQUIRED(HttpStatus.CONFLICT, "배송지를 먼저 입력해 주세요."),
	NO_ORDERABLE_ITEMS(HttpStatus.CONFLICT, "주문할 수 있는 상품이 없습니다."),
	ORDER_AMOUNT_INVALID(HttpStatus.CONFLICT, "결제할 금액이 없는 주문은 만들 수 없습니다."),

	// 결제
	PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않습니다."),
	PAYMENT_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "결제에 실패했습니다."),
	PAYMENT_IN_PROGRESS(HttpStatus.CONFLICT, "이미 결제가 진행 중이거나 완료된 주문입니다."),
	PAYMENT_PENDING_CONFIRMATION(HttpStatus.ACCEPTED, "결제 결과를 확인 중입니다. 잠시 후 주문 내역에서 확인해 주세요."),
	CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "취소할 수 없는 상태입니다."),
	CANCEL_FAILED(HttpStatus.BAD_GATEWAY, "결제 취소에 실패했습니다. 잠시 후 다시 시도해 주세요."),

	// 고객지원
	NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다."),
	FAQ_NOT_FOUND(HttpStatus.NOT_FOUND, "FAQ를 찾을 수 없습니다."),
	INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다."),

	// 견적
	QUOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "견적 문의를 찾을 수 없습니다.");

	private final HttpStatus status;
	private final String message;
}
