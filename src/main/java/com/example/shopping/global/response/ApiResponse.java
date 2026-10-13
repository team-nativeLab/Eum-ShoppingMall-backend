package com.example.shopping.global.response;

import java.time.OffsetDateTime;
import java.util.List;

import com.example.shopping.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

/** 모든 API가 공유하는 응답 포맷. 프론트는 code 값으로 분기한다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String code, String message, T data,
		List<FieldError> errors, OffsetDateTime timestamp) {

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
