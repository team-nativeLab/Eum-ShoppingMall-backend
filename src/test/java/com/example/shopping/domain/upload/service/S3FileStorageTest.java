package com.example.shopping.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.shopping.domain.upload.service.PresignedUploadStorage.PresignedUpload;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 실제 S3에는 연결하지 않는다. 주소 서명(Presigner)은 S3를 부르지 않는 계산이라 시험용 키로 실제로 서명해 본다. */
class S3FileStorageTest {

	private static final String KEY = "products/2026/10/" + UUID.randomUUID() + ".png";

	private final S3Client client = mock(S3Client.class);
	private final S3FileStorage storage = new S3FileStorage(client, testPresigner(), "private-image-bucket",
			"https://cdn.example.com/", 10);

	static S3Presigner testPresigner() {
		return S3Presigner.builder()
				.region(Region.AP_NORTHEAST_2)
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret")))
				.build();
	}

	@Test
	void S3에_불변_캐시와_MIME을_설정하고_CDN_주소를_반환한다() {
		String url = storage.store(new byte[] {1, 2, 3}, "webp");

		ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(client).putObject(request.capture(), any(RequestBody.class));
		assertThat(request.getValue().bucket()).isEqualTo("private-image-bucket");
		assertThat(request.getValue().key()).matches("products/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.webp");
		assertThat(request.getValue().contentType()).isEqualTo("image/webp");
		assertThat(request.getValue().cacheControl()).isEqualTo("public, max-age=31536000, immutable");
		assertThat(url).isEqualTo("https://cdn.example.com/" + request.getValue().key());
	}

	@Test
	void S3에_저장하지_못하면_503_UPLOAD_FAILED로_알린다() {
		when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
				.thenThrow(SdkClientException.create("연결 실패"));

		assertThatThrownBy(() -> storage.store(new byte[] {1}, "png"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.UPLOAD_FAILED);
	}

	@Test
	void 직접_업로드_주소는_서버가_정한_경로에_형식과_크기를_서명에_넣어_만든다() {
		Instant before = Instant.now();
		PresignedUpload upload = storage.presign("png", "image/png", 2048);

		assertThat(upload.key()).matches("products/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png");
		assertThat(upload.url())
				.startsWith("https://private-image-bucket.s3.ap-northeast-2.amazonaws.com/" + upload.key())
				.contains("X-Amz-Expires=600", "X-Amz-Signature=");
		Map<String, String> headers = upload.headers().entrySet().stream()
				.collect(Collectors.toMap(e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));
		assertThat(headers)
				.containsEntry("content-type", "image/png")
				.containsEntry("content-length", "2048")
				.containsEntry("cache-control", "public, max-age=31536000, immutable")
				.doesNotContainKey("host");
		assertThat(upload.expiresAt()).isBetween(before.plus(Duration.ofMinutes(10)).minusSeconds(5),
				Instant.now().plus(Duration.ofMinutes(10)));
		verifyNoInteractions(client);
	}

	@Test
	void 이_서버가_만든_경로가_아니면_확인도_삭제도_하지_않는다() {
		for (String key : new String[] { "other/secret.txt", "products/../admin/a.png", "products/2026/10/a.png" }) {
			assertThatThrownBy(() -> storage.inspect(key, 12))
					.extracting(e -> ((BusinessException) e).getErrorCode())
					.isEqualTo(ErrorCode.UPLOAD_NOT_FOUND);
			storage.delete(key);
		}
		verifyNoInteractions(client);
	}

	@Test
	void 올라오지_않은_파일을_확인하면_404_UPLOAD_NOT_FOUND() {
		when(client.headObject(any(HeadObjectRequest.class)))
				.thenThrow(NoSuchKeyException.builder().statusCode(404).message("Not Found").build());

		assertThatThrownBy(() -> storage.inspect(KEY, 12))
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.UPLOAD_NOT_FOUND);
	}

	@Test
	void 파일을_지우지_못해도_예외를_던지지_않는다() {
		when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(SdkClientException.create("연결 실패"));

		storage.delete(KEY);

		verify(client).deleteObject(any(DeleteObjectRequest.class));
	}

	@Test
	void 이미지_주소는_이_서버의_CDN_주소와_키_형식만_받는다() {
		assertThat(storage.isStoredUrl("https://cdn.example.com/" + KEY)).isTrue();
		assertThat(storage.isStoredUrl("https://cdn.example.com/products/legacy/" + UUID.randomUUID() + ".jpg"))
				.isTrue();
		assertThat(storage.isStoredUrl("https://other.example.com/" + KEY)).isFalse();
		assertThat(storage.isStoredUrl("https://cdn.example.com.evil.com/" + KEY)).isFalse();
		assertThat(storage.isStoredUrl("https://cdn.example.com/" + KEY + "?v=1")).isFalse();
		assertThat(storage.isStoredUrl("https://cdn.example.com/products/a.png")).isFalse();
		assertThat(storage.isStoredUrl("/files/a.png")).isFalse();
	}

	@Test
	void CDN_주소가_바뀌어도_예전_주소에서_키를_찾는다() {
		assertThat(storage.keyOf("https://old-cdn.cloudfront.net/" + KEY)).isEqualTo(KEY);
		assertThat(storage.keyOf("https://cdn.example.com/" + KEY)).isEqualTo(KEY);
		assertThat(storage.keyOf("https://cdn.example.com/other/a.png")).isNull();
		assertThat(storage.keyOf("/files/a.png")).isNull();
		assertThat(storage.keyOf("not a url")).isNull();
	}

	@Test
	void 기준_시각_전에_올라온_이_서버의_파일만_목록으로_넘긴다() {
		Instant cutoff = Instant.parse("2026-10-20T00:00:00Z");
		String old = "products/2026/09/" + UUID.randomUUID() + ".png";
		String fresh = "products/2026/10/" + UUID.randomUUID() + ".png";
		ListObjectsV2Response page = ListObjectsV2Response.builder().contents(
				S3Object.builder().key(old).lastModified(cutoff.minusSeconds(1)).build(),
				S3Object.builder().key(fresh).lastModified(cutoff.plusSeconds(1)).build(),
				S3Object.builder().key("products/readme.txt").lastModified(cutoff.minusSeconds(1)).build()).build();
		when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page);
		when(client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
				.thenAnswer(invocation -> new ListObjectsV2Iterable(client, invocation.getArgument(0)));

		List<String> keys = new ArrayList<>();
		storage.forEachKeyStoredBefore(cutoff, keys::addAll);

		assertThat(keys).containsExactly(old);
		ArgumentCaptor<ListObjectsV2Request> request = ArgumentCaptor.forClass(ListObjectsV2Request.class);
		verify(client, times(1)).listObjectsV2(request.capture());
		assertThat(request.getValue().bucket()).isEqualTo("private-image-bucket");
		assertThat(request.getValue().prefix()).isEqualTo("products/");
	}

	@Test
	void 직접_업로드_주소의_유효_시간은_3분에서_60분_사이여야_한다() {
		assertThatThrownBy(() -> new S3FileStorage(client, testPresigner(), "bucket", "https://cdn.example.com", 2))
				.hasMessageContaining("UPLOAD_PRESIGNED_URL_EXPIRATION_MINUTES");
		assertThatThrownBy(() -> new S3FileStorage(client, testPresigner(), "bucket", "https://cdn.example.com", 61))
				.hasMessageContaining("UPLOAD_PRESIGNED_URL_EXPIRATION_MINUTES");
	}
}
