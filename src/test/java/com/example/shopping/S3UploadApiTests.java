package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.upload.service.FileStorage;
import com.example.shopping.domain.upload.service.LocalFileStorage;
import com.example.shopping.domain.upload.service.S3FileStorage;
import com.jayway.jsonpath.JsonPath;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** upload.storage=s3일 때의 업로드 API. S3 클라이언트는 가짜로 바꾼다. */
@SpringBootTest(properties = { "upload.storage=s3", "upload.s3.bucket=shop-images",
		"upload.s3.region=ap-northeast-2", "upload.s3.cdn-base-url=https://cdn.example.com",
		"upload.s3.access-key=TESTACCESSKEY", "upload.s3.secret-key=test-secret" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class S3UploadApiTests {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ApplicationContext context;

	@MockitoBean
	private S3Client s3Client;

	@Test
	void 업로드_API가_S3에_저장하고_CDN_주소를_돌려준다() throws Exception {
		assertThat(context.getBean(FileStorage.class)).isInstanceOf(S3FileStorage.class);
		assertThat(context.getBeansOfType(LocalFileStorage.class)).isEmpty();

		mockMvc.perform(multipart("/api/v1/uploads").file(new MockMultipartFile("file", "a.png", "image/png", PNG)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.url").value(Matchers.matchesPattern(
						"https://cdn\\.example\\.com/products/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png")));
		verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
	}

	@Test
	void 직접_업로드_주소를_받아_올린_뒤_완료_요청을_보내면_이미지_주소를_돌려준다() throws Exception {
		String key = presign("image/png", 12);
		uploaded(PNG);

		complete(key)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.url").value("https://cdn.example.com/" + key));
		verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));

		// 받은 주소는 상품 이미지로 등록할 수 있다.
		postJson("/api/v1/admin/products", """
				{"name":"안전모","basePrice":1000,"status":"HIDDEN","imageUrl":"https://cdn.example.com/%s"}
				""".formatted(key)).andExpect(status().isCreated());
	}

	@Test
	void 직접_올린_파일이_이미지가_아니거나_고른_형식과_다르면_지우고_400으로_거절한다() throws Exception {
		String notImage = presign("image/png", 12);
		uploaded("<html>sneaky".getBytes());
		complete(notImage).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FILE"));

		// PNG로 주소를 받고 JPEG를 올린 경우
		String mismatched = presign("image/png", 12);
		uploaded(JPEG);
		complete(mismatched).andExpect(status().isBadRequest());

		ArgumentCaptor<DeleteObjectRequest> deleted = ArgumentCaptor.forClass(DeleteObjectRequest.class);
		verify(s3Client, times(2)).deleteObject(deleted.capture());
		assertThat(deleted.getAllValues()).extracting(DeleteObjectRequest::key).containsExactly(notImage, mismatched);
	}

	@Test
	void 이_서버가_발급하지_않은_경로로는_완료_요청을_할_수_없다() throws Exception {
		complete("products/someone-else.png")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("UPLOAD_NOT_FOUND"));
		verifyNoInteractions(s3Client);
	}

	@Test
	void 이미지가_아닌_형식이나_5MB를_넘는_크기로는_주소를_받을_수_없다() throws Exception {
		presignRequest("image/svg+xml", 100).andExpect(status().isBadRequest());
		presignRequest("video/mp4", 100).andExpect(status().isBadRequest());
		presignRequest("image/png", 5L * 1024 * 1024 + 1).andExpect(status().isBadRequest());
		presignRequest("image/png", 0).andExpect(status().isBadRequest());
		presignRequest("image/png", 5L * 1024 * 1024).andExpect(status().isOk());
	}

	@Test
	void S3_저장소에서는_다른_호스트나_로컬_경로를_이미지_주소로_받지_않는다() throws Exception {
		postJson("/api/v1/admin/banners", """
				{"imageUrl":"/files/a.png"}
				""").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE_URL"));
		postJson("/api/v1/admin/banners", """
				{"imageUrl":"https://shop-images.s3.ap-northeast-2.amazonaws.com/products/2026/10/a.png"}
				""").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE_URL"));
	}

	private String presign(String contentType, long contentLength) throws Exception {
		String body = presignRequest(contentType, contentLength)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.presignedUrl").value(Matchers.startsWith(
						"https://shop-images.s3.ap-northeast-2.amazonaws.com/products/")))
				// 설정한 액세스 키로 서명한다.
				.andExpect(jsonPath("$.data.presignedUrl").value(Matchers.containsString(
						"X-Amz-Credential=TESTACCESSKEY%2F")))
				.andExpect(jsonPath("$.data.headers.content-type").value(contentType))
				.andExpect(jsonPath("$.data.expiresAt").exists())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.fileKey");
	}

	private ResultActions presignRequest(String contentType, long length) throws Exception {
		return postJson("/api/v1/uploads/presigned-url", """
				{"contentType":"%s","contentLength":%d}
				""".formatted(contentType, length));
	}

	private ResultActions complete(String key) throws Exception {
		return postJson("/api/v1/uploads/complete", """
				{"fileKey":"%s"}
				""".formatted(key));
	}

	private ResultActions postJson(String url, String body) throws Exception {
		return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** S3에 이 내용의 파일이 올라와 있는 것처럼 만든다. */
	private void uploaded(byte[] content) {
		when(s3Client.headObject(any(HeadObjectRequest.class)))
				.thenReturn(HeadObjectResponse.builder().contentLength((long) content.length).build());
		when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
				.thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content));
	}
}
