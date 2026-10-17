package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.shopping.domain.upload.service.FileStorage;
import com.example.shopping.domain.upload.service.LocalFileStorage;
import com.example.shopping.domain.upload.service.S3FileStorage;
import com.example.shopping.domain.upload.service.S3StorageConfig;
import com.example.shopping.domain.upload.service.S3UploadProperties;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** S3 이미지 저장 (upload.storage=s3). 실제 S3에는 연결하지 않고 S3 클라이언트를 가짜로 바꿔 확인한다. */
class S3FileStorageTests {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

	private final S3Client s3Client = mock(S3Client.class);
	private final S3FileStorage storage = new S3FileStorage(s3Client,
			new S3UploadProperties("shop-images", "ap-northeast-2", "https://cdn.example.com/", "images"));

	@Test
	void 서버가_정한_경로로_저장하고_캐시_헤더를_붙이며_CDN_주소를_돌려준다() {
		String url = storage.store(PNG, "png");

		ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
		ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
		verify(s3Client).putObject(request.capture(), body.capture());
		assertThat(request.getValue().bucket()).isEqualTo("shop-images");
		assertThat(request.getValue().key()).matches("images/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png");
		assertThat(request.getValue().contentType()).isEqualTo("image/png");
		assertThat(request.getValue().cacheControl()).isEqualTo("public, max-age=31536000, immutable");
		assertThat(body.getValue().optionalContentLength()).hasValue((long) PNG.length);
		// 설정 끝의 /는 겹치지 않는다.
		assertThat(url).isEqualTo("https://cdn.example.com/" + request.getValue().key());
	}

	@Test
	void 확장자에_맞는_Content_Type으로_저장한다() {
		storage.store(PNG, "jpg");
		storage.store(PNG, "webp");

		ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3Client, org.mockito.Mockito.times(2)).putObject(request.capture(), any(RequestBody.class));
		assertThat(request.getAllValues()).extracting(PutObjectRequest::contentType)
				.containsExactly("image/jpeg", "image/webp");
	}

	@Test
	void S3에_저장하지_못하면_503_UPLOAD_FAILED로_알린다() {
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
				.thenThrow(SdkClientException.create("연결 실패"));

		assertThatThrownBy(() -> storage.store(PNG, "png"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.UPLOAD_FAILED);
	}

	@Test
	void S3를_고르고_버킷이나_CDN_주소를_설정하지_않으면_서버가_뜨지_않는다() {
		new ApplicationContextRunner()
				.withUserConfiguration(S3StorageConfig.class)
				.withPropertyValues("upload.storage=s3", "upload.s3.region=ap-northeast-2", "upload.s3.key-prefix=images")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).rootCause()
							.hasMessageContaining("UPLOAD_S3_BUCKET")
							.hasMessageContaining("UPLOAD_S3_PUBLIC_URL");
				});
	}

	/** 업로드 API가 S3 저장소로 연결되는지 확인한다. */
	@Nested
	@SpringBootTest(properties = { "upload.storage=s3", "upload.s3.bucket=shop-images",
			"upload.s3.public-url=https://cdn.example.com" })
	@AutoConfigureMockMvc
	@ActiveProfiles("test")
	class UploadApi {

		@Autowired
		private MockMvc mockMvc;

		@Autowired
		private ApplicationContext context;

		@MockitoBean
		private S3Client mockS3Client;

		@Test
		void upload_storage가_s3면_업로드_API가_S3에_저장하고_CDN_주소를_돌려준다() throws Exception {
			assertThat(context.getBean(FileStorage.class)).isInstanceOf(S3FileStorage.class);
			assertThat(context.getBeansOfType(LocalFileStorage.class)).isEmpty();

			mockMvc.perform(multipart("/api/v1/uploads").file(new MockMultipartFile("file", "a.png", "image/png", PNG))
							.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.url").value(Matchers.matchesPattern(
							"https://cdn\\.example\\.com/images/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png")));
			verify(mockS3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
		}

		@Test
		void 이미지가_아닌_파일은_S3에_올리지_않고_거절한다() throws Exception {
			mockMvc.perform(multipart("/api/v1/uploads")
							.file(new MockMultipartFile("file", "a.png", "image/png", "<script>".getBytes()))
							.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_FILE"));
			verify(mockS3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
		}
	}
}
