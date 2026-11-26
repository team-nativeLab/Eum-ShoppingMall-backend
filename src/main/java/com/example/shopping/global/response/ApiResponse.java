package com.example.shopping.global.response;

import java.time.OffsetDateTime;
import java.util.List;

import com.example.shopping.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 API가 공유하는 응답 포맷. 프론트는 code 값으로 분기한다.
 *
 * @param success   성공하면 true, 실패하면 false
 * @param code      결과 코드. 성공은 SUCCESS, 실패는 오류 코드(문서 첫 화면의 오류 코드 표). 화면은 이 값으로 분기한다.
 * @param message   사용자에게 보여 줄 안내 문구. 성공은 OK
 * @param data      응답 값. 실패했거나 돌려줄 값이 없으면 빠진다.
 * @param errors    입력값 검증 실패(400 INVALID_INPUT)일 때만 있는 항목별 사유
 * @param timestamp 응답 시각(서버 시간대 오프셋 포함, 예: 2026-11-14T12:00:00+09:00)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String code, String message, T data,
		List<FieldError> errors, OffsetDateTime timestamp) {

	/**
	 * 입력값 검증에 실패한 항목 하나
	 *
	 * @param field  요청 항목 이름(예: email, items[0].quantity)
	 * @param reason 사유
	 */
	public record FieldError(String field, String reason) {
	}

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, "SUCCESS", "OK", data, null, OffsetDateTime.now());
	}

	public static ApiResponse<Void> fail(ErrorCode errorCode) {
		return fail(errorCode.name(), errorCode.getMessage(), null);
	}

	public static ApiResponse<Void> fail(String code, String message, List<FieldError> errors) {
		return new ApiResponse<>(false, code, message, null, errors, OffsetDateTime.now());
	}
}
