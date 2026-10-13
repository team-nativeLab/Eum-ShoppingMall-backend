package com.example.shopping.global.security;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 요청 본문 크기를 제한한다. 제한이 없으면 로그인 같은 공개 API에 아주 큰 본문을 보내 서버 메모리를 고갈시킬 수 있다.
 * 파일 업로드(multipart)는 여기서 다루지 않고 spring.servlet.multipart 설정이 따로 제한한다.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

	private final long maxBytes;
	private final HandlerExceptionResolver resolver;

	public RequestSizeLimitFilter(long maxBytes, HandlerExceptionResolver resolver) {
		this.maxBytes = maxBytes;
		this.resolver = resolver;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String contentType = request.getContentType();
		if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
			chain.doFilter(request, response);
			return;
		}
		// 크기를 미리 알려준 요청은 읽기 전에 거절한다.
		if (request.getContentLengthLong() > maxBytes) {
			resolver.resolveException(request, response, null, new BusinessException(ErrorCode.REQUEST_TOO_LARGE));
			return;
		}
		// 크기를 알려주지 않는 전송 방식(chunked)은 읽다가 한도를 넘는 순간 중단시킨다.
		chain.doFilter(new SizeLimitedRequest(request, maxBytes), response);
	}

	private static class SizeLimitedRequest extends HttpServletRequestWrapper {

		private final long maxBytes;

		SizeLimitedRequest(HttpServletRequest request, long maxBytes) {
			super(request);
			this.maxBytes = maxBytes;
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			return new SizeLimitedInputStream(super.getInputStream(), maxBytes);
		}
	}

	private static class SizeLimitedInputStream extends ServletInputStream {

		private final ServletInputStream delegate;
		private final long maxBytes;
		private long readBytes;

		SizeLimitedInputStream(ServletInputStream delegate, long maxBytes) {
			this.delegate = delegate;
			this.maxBytes = maxBytes;
		}

		@Override
		public int read() throws IOException {
			int value = delegate.read();
			if (value != -1) {
				count(1);
			}
			return value;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			int read = delegate.read(buffer, offset, length);
			if (read > 0) {
				count(read);
			}
			return read;
		}

		private void count(int bytes) throws IOException {
			readBytes += bytes;
			if (readBytes > maxBytes) {
				throw new IOException("요청 본문이 허용 크기(" + maxBytes + "바이트)를 넘었습니다.");
			}
		}

		@Override
		public boolean isFinished() {
			return delegate.isFinished();
		}

		@Override
		public boolean isReady() {
			return delegate.isReady();
		}

		@Override
		public void setReadListener(ReadListener listener) {
			delegate.setReadListener(listener);
		}
	}
}
