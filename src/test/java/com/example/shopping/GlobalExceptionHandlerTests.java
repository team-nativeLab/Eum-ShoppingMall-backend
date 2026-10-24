package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLTransientConnectionException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.example.shopping.global.exception.GlobalExceptionHandler;
import com.example.shopping.global.response.ApiResponse;

/** 실제 요청으로는 재현하기 어려운 예외(용량 초과, DB 락 충돌, 예상 못 한 오류)가 어떤 응답으로 바뀌는지 확인한다. */
class GlobalExceptionHandlerTests {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void 업로드_용량_초과는_413과_안내_메시지로_응답한다() {
		ResponseEntity<ApiResponse<Void>> response = handler
				.handleBadRequest(new MaxUploadSizeExceededException(5 * 1024 * 1024));

		assertThat(response.getStatusCode().value()).isEqualTo(413);
		assertThat(response.getBody().code()).isEqualTo("FILE_TOO_LARGE");
		assertThat(response.getBody().message()).contains("5MB");
	}

	@Test
	void DB_락_충돌과_동시_수정과_제약_위반은_500이_아니라_409로_응답한다() {
		assertConflict(handler.handleConcurrentUpdate(new CannotAcquireLockException("Deadlock found")));
		assertConflict(handler.handleConcurrentUpdate(
				new ObjectOptimisticLockingFailureException("Order", 1L)));
		assertConflict(handler.handleConcurrentUpdate(new DataIntegrityViolationException("Duplicate entry")));
	}

	@Test
	void 예상하지_못한_오류는_내부_정보를_노출하지_않고_500으로_응답한다() {
		ResponseEntity<ApiResponse<Void>> response = handler
				.handleUnexpected(new IllegalStateException("jdbc:mysql://db-host/shopping 연결 실패"));

		assertThat(response.getStatusCode().value()).isEqualTo(500);
		assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
		assertThat(response.getBody().message()).doesNotContain("jdbc");
	}

	@Test
	void DB_커넥션_대기_시간_초과는_503과_Retry_After로_응답한다() {
		CannotGetJdbcConnectionException exception = new CannotGetJdbcConnectionException("pool exhausted",
				new SQLTransientConnectionException("Connection is not available, request timed out"));

		ResponseEntity<ApiResponse<Void>> response = handler.handleUnexpected(exception);

		assertThat(response.getStatusCode().value()).isEqualTo(503);
		assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("3");
		assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
	}

	private void assertConflict(ResponseEntity<ApiResponse<Void>> response) {
		assertThat(response.getStatusCode().value()).isEqualTo(409);
		assertThat(response.getBody().code()).isEqualTo("CONCURRENT_UPDATE");
	}
}
