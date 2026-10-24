package com.example.shopping.global.exception;

import java.sql.SQLTransientConnectionException;
import java.util.List;

import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

import com.example.shopping.global.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final String INVALID_FORMAT = "값의 형식이 올바르지 않습니다.";

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
		return toResponse(e.getErrorCode());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		// 형 변환 실패(숫자 자리에 문자 등)의 기본 메시지에는 서버 내부의 클래스 이름이 들어 있어 그대로 내보내지 않는다.
		List<ApiResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
				.map(error -> new ApiResponse.FieldError(error.getField(),
						error.isBindingFailure() ? INVALID_FORMAT : error.getDefaultMessage()))
				.toList();
		ErrorCode errorCode = ErrorCode.INVALID_INPUT;
		return ResponseEntity.status(errorCode.getStatus())
				.body(ApiResponse.fail(errorCode.name(), errorCode.getMessage(), errors));
	}

	/**
	 * 요청 자체가 잘못된 경우: 본문이 없거나 JSON이 깨짐, 파라미터 타입 불일치(숫자 자리에 문자 등),
	 * 없는 정렬 기준, multipart가 아닌 업로드 요청.
	 */
	@ExceptionHandler({ HttpMessageNotReadableException.class, TypeMismatchException.class,
			PropertyReferenceException.class, MultipartException.class })
	public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
		// 업로드 용량 초과(413)처럼 스프링이 상태코드를 정해 둔 예외는 그 상태코드를 따른다.
		if (e instanceof ErrorResponse errorResponse) {
			return toResponse(errorResponse);
		}
		return toResponse(ErrorCode.INVALID_INPUT);
	}

	/**
	 * 같은 데이터를 두 요청이 동시에 바꾸려다 한쪽이 밀려난 경우: 낙관적 락 충돌, DB 락 대기 시간 초과·교착,
	 * 유니크 제약 위반(장바구니에 같은 상품을 동시에 담거나 같은 멱등 키로 주문을 두 번 보내는 등).
	 * 다시 시도하면 되는 상황이라 500이 아닌 409로 응답한다.
	 */
	@ExceptionHandler({ ConcurrencyFailureException.class, DataIntegrityViolationException.class })
	public ResponseEntity<ApiResponse<Void>> handleConcurrentUpdate(Exception e) {
		log.warn("동시 요청 충돌 또는 DB 제약 위반: {}", e.getMessage());
		return toResponse(ErrorCode.CONCURRENT_UPDATE);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
		return toResponse(ErrorCode.FORBIDDEN);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
		if (isDatabaseCapacityTimeout(e)) {
			log.warn("DB 커넥션 또는 쿼리 대기 시간 초과", e);
			return serviceUnavailableResponse();
		}
		// 없는 주소(404), 잘못된 요청 방식(405), 필수 파라미터 누락(400) 같은 스프링 MVC 표준 예외
		if (e instanceof ErrorResponse errorResponse) {
			return toResponse(errorResponse);
		}
		log.error("처리되지 않은 예외", e);
		return toResponse(ErrorCode.INTERNAL_ERROR);
	}

	private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode) {
		return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode));
	}

	private boolean isDatabaseCapacityTimeout(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLTransientConnectionException
					|| cause instanceof TransactionTimedOutException
					|| cause instanceof QueryTimeoutException
					|| cause instanceof jakarta.persistence.QueryTimeoutException) {
				return true;
			}
		}
		return false;
	}

	private ResponseEntity<ApiResponse<Void>> serviceUnavailableResponse() {
		return ResponseEntity.status(ErrorCode.SERVICE_UNAVAILABLE.getStatus())
				.header(HttpHeaders.RETRY_AFTER, "3")
				.body(ApiResponse.fail(ErrorCode.SERVICE_UNAVAILABLE));
	}

	/** 스프링이 정한 상태코드는 유지하되, 코드와 메시지는 우리 에러 코드로 바꿔 내려준다. */
	private ResponseEntity<ApiResponse<Void>> toResponse(ErrorResponse errorResponse) {
		int status = errorResponse.getStatusCode().value();
		ErrorCode errorCode = switch (status) {
			case 400 -> ErrorCode.INVALID_INPUT;
			case 404 -> ErrorCode.NOT_FOUND;
			case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
			case 413 -> ErrorCode.FILE_TOO_LARGE;
			case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
			default -> null;
		};
		if (errorCode != null) {
			return toResponse(errorCode);
		}
		HttpStatus httpStatus = HttpStatus.valueOf(status);
		return ResponseEntity.status(httpStatus)
				.body(ApiResponse.fail(httpStatus.name(), httpStatus.getReasonPhrase(), null));
	}
}
