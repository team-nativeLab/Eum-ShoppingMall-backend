package com.example.shopping.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.shopping.domain.upload.dto.request.PresignedUploadCompleteRequest;
import com.example.shopping.domain.upload.dto.request.PresignedUrlRequest;
import com.example.shopping.domain.upload.dto.response.PresignedUrlResponse;
import com.example.shopping.domain.upload.service.PresignedUploadStorage.PresignedUpload;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3와 같은 API를 쓰는 RustFS를 컨테이너로 띄워 사진을 실제로 올리고 내려받아 본다.
 * 서명도 실제로 검사하므로 직접 업로드 주소의 형식·크기 제한까지 확인할 수 있다.
 * Docker가 없으면 건너뛴다. CI(GitHub Actions)에는 Docker가 있어 항상 실행된다.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3FileStorageContainerTest {

	private static final String BUCKET = "shop-images";
	private static final String CDN = "https://cdn.example.com";

	// 1x1 PNG 사진
	private static final byte[] PNG = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

	private static final String ACCESS_KEY = "test-access-key";
	private static final String SECRET_KEY = "test-secret-key";

	@Container
	private static final GenericContainer<?> S3 = new GenericContainer<>("rustfs/rustfs:1.0.2")
			.withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
			.withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
			.withExposedPorts(9000)
			.waitingFor(Wait.forHttp("/health").forPort(9000).forStatusCode(200));

	private static S3Client s3Client;
	private static S3Presigner s3Presigner;
	private static S3FileStorage storage;
	private static UploadService uploadService;

	private final HttpClient http = HttpClient.newHttpClient();

	@BeforeAll
	static void setUp() {
		URI endpoint = URI.create("http://" + S3.getHost() + ":" + S3.getMappedPort(9000));
		StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
				AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY));
		s3Client = S3Client.builder()
				.endpointOverride(endpoint)
				.region(Region.AP_NORTHEAST_2)
				.credentialsProvider(credentials)
				.forcePathStyle(true)
				.build();
		s3Presigner = S3Presigner.builder()
				.endpointOverride(endpoint)
				.region(Region.AP_NORTHEAST_2)
				.credentialsProvider(credentials)
				.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
				.build();
		s3Client.createBucket(request -> request.bucket(BUCKET));

		storage = new S3FileStorage(s3Client, s3Presigner, BUCKET, CDN, 10);
		DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
		beans.registerSingleton("storage", storage);
		uploadService = new UploadService(storage, beans.getBeanProvider(PresignedUploadStorage.class));
	}

	@AfterAll
	static void tearDown() {
		s3Presigner.close();
		s3Client.close();
	}

	@Test
	void 서버를_거쳐_올린_사진이_버킷에_그대로_저장된다() {
		String url = storage.store(PNG, "png");

		String key = url.substring(CDN.length() + 1);
		ResponseBytes<GetObjectResponse> saved = s3Client.getObjectAsBytes(request -> request.bucket(BUCKET).key(key));
		assertThat(saved.asByteArray()).isEqualTo(PNG);
		assertThat(saved.response().contentType()).isEqualTo("image/png");
		assertThat(saved.response().cacheControl()).isEqualTo("public, max-age=31536000, immutable");
		assertThat(storage.isStoredUrl(url)).isTrue();
	}

	@Test
	void 직접_업로드_주소로_올린_사진을_완료_처리하면_이미지_주소를_받는다() throws Exception {
		PresignedUrlResponse presigned = uploadService.getPresignedUrl(new PresignedUrlRequest("image/png",
				(long) PNG.length));

		assertThat(put(presigned.presignedUrl(), presigned.headers(), PNG)).isEqualTo(200);
		String url = uploadService.completePresignedUpload(new PresignedUploadCompleteRequest(presigned.fileKey()))
				.url();

		assertThat(url).isEqualTo(CDN + "/" + presigned.fileKey());
		assertThat(s3Client.getObjectAsBytes(request -> request.bucket(BUCKET).key(presigned.fileKey()))
				.asByteArray()).isEqualTo(PNG);
	}

	@Test
	void 사진이_아닌_파일을_직접_올리면_완료_요청에서_거절하고_버킷에서_지운다() throws Exception {
		byte[] html = "<html><script>alert(1)</script></html>".getBytes();
		PresignedUrlResponse presigned = uploadService.getPresignedUrl(new PresignedUrlRequest("image/png",
				(long) html.length));
		assertThat(put(presigned.presignedUrl(), presigned.headers(), html)).isEqualTo(200);

		assertThatThrownBy(() -> uploadService.completePresignedUpload(
				new PresignedUploadCompleteRequest(presigned.fileKey())))
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_FILE);
		assertThatThrownBy(() -> s3Client.headObject(request -> request.bucket(BUCKET).key(presigned.fileKey())))
				.isInstanceOf(NoSuchKeyException.class);
	}

	@Test
	void 직접_업로드_주소는_서명한_형식과_크기가_아니면_S3가_거절한다() throws Exception {
		PresignedUpload upload = storage.presign("png", "image/png", 2048);

		// 크기를 속인 경우
		assertThat(put(upload.url(), upload.headers(), PNG)).isEqualTo(403);
		// 형식을 바꾼 경우
		Map<String, String> otherType = new java.util.LinkedHashMap<>(upload.headers());
		otherType.replaceAll((name, value) -> "content-type".equalsIgnoreCase(name) ? "text/html" : value);
		assertThat(put(upload.url(), otherType, new byte[2048])).isEqualTo(403);

		assertThatThrownBy(() -> storage.inspect(upload.key(), 12))
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.UPLOAD_NOT_FOUND);
	}

	@Test
	void 정리_작업은_저장된_사진을_목록으로_찾고_지울_수_있다() {
		String key = storage.store(PNG, "png").substring(CDN.length() + 1);

		List<String> listed = new ArrayList<>();
		storage.forEachKeyStoredBefore(Instant.now().plusSeconds(60), listed::addAll);
		assertThat(listed).contains(key);

		storage.delete(key);
		assertThatThrownBy(() -> s3Client.headObject(request -> request.bucket(BUCKET).key(key)))
				.isInstanceOf(NoSuchKeyException.class);
	}

	/** 브라우저처럼 응답의 headers를 그대로 넣어 PUT으로 올린다. Content-Length는 HttpClient가 본문 크기로 붙인다. */
	private int put(String url, Map<String, String> headers, byte[] body) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
				.PUT(HttpRequest.BodyPublishers.ofByteArray(body));
		headers.forEach((name, value) -> {
			if (!"content-length".equalsIgnoreCase(name)) {
				request.header(name, value);
			}
		});
		return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
	}
}
