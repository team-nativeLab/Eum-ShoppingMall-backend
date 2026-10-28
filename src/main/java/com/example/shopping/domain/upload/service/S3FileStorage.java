package com.example.shopping.domain.upload.service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * 비공개 버킷에 저장하고 CDN(CloudFront) 주소를 돌려준다.
 * 버킷은 공개하지 않고 CDN으로만 내보내는 구성을 전제로 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "upload.storage", havingValue = "s3")
public class S3FileStorage implements FileStorage, PresignedUploadStorage {

	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy/MM");
	// 파일 이름이 매번 새로 만든 UUID라 같은 주소의 내용은 바뀌지 않는다. 브라우저·CDN이 1년 동안 그대로 쓰게 한다.
	private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";
	private static final String PREFIX = "products/";

	// 이 서버가 만든 키만 확인·삭제한다. legacy/는 로컬 저장소에서 옮겨 온 파일이다 (scripts/migrate-local-uploads-to-s3.sh).
	private static final Pattern OWN_KEY = Pattern.compile(
			"^products/(\\d{4}/\\d{2}|legacy)/[0-9a-f-]{36}\\.(jpg|png|gif|webp)$");

	private static final Map<String, String> CONTENT_TYPES = Map.of(
			"jpg", "image/jpeg",
			"png", "image/png",
			"gif", "image/gif",
			"webp", "image/webp");

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final String bucket;
	private final String cdnBaseUrl;
	private final String cdnBasePath;
	private final Duration presignedUrlExpiration;

	public S3FileStorage(S3Client s3Client, S3Presigner s3Presigner, @Value("${upload.s3.bucket}") String bucket,
			@Value("${upload.s3.cdn-base-url}") String cdnBaseUrl,
			@Value("${upload.s3.presigned-url-expiration-minutes:10}") long presignedUrlExpirationMinutes) {
		Assert.hasText(bucket, "UPLOAD_S3_BUCKET is required when UPLOAD_STORAGE=s3");
		URI cdnUri = URI.create(cdnBaseUrl);
		Assert.isTrue("https".equalsIgnoreCase(cdnUri.getScheme()) && cdnUri.getHost() != null,
				"UPLOAD_CDN_BASE_URL must be an HTTPS URL");
		// 너무 길면 유출된 주소로 오래 올릴 수 있고, 너무 짧으면 느린 네트워크에서 올리다 만료된다.
		Assert.isTrue(presignedUrlExpirationMinutes >= 3 && presignedUrlExpirationMinutes <= 60,
				"UPLOAD_PRESIGNED_URL_EXPIRATION_MINUTES must be between 3 and 60");
		this.s3Client = s3Client;
		this.s3Presigner = s3Presigner;
		this.bucket = bucket;
		this.cdnBaseUrl = cdnBaseUrl.replaceAll("/+$", "");
		this.cdnBasePath = URI.create(this.cdnBaseUrl).getPath();
		this.presignedUrlExpiration = Duration.ofMinutes(presignedUrlExpirationMinutes);
	}

	@Override
	public String store(byte[] content, String extension) {
		String key = newKey(extension);
		try {
			s3Client.putObject(PutObjectRequest.builder()
					.bucket(bucket)
					.key(key)
					.contentType(CONTENT_TYPES.get(extension))
					.cacheControl(CACHE_CONTROL)
					.build(), RequestBody.fromBytes(content));
		} catch (SdkException e) {
			log.error("S3에 이미지를 저장하지 못했습니다: bucket={}, key={}", bucket, key, e);
			throw new BusinessException(ErrorCode.UPLOAD_FAILED);
		}
		return publicUrl(key);
	}

	/**
	 * 형식(Content-Type)과 크기(Content-Length)를 서명에 넣는다.
	 * 클라이언트가 다른 형식이나 크기로 올리면 서명이 맞지 않아 S3가 거절한다.
	 */
	@Override
	public PresignedUpload presign(String extension, String contentType, long contentLength) {
		String key = newKey(extension);
		PutObjectRequest put = PutObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.contentType(contentType)
				.contentLength(contentLength)
				.cacheControl(CACHE_CONTROL)
				.build();
		PresignedPutObjectRequest presigned;
		try {
			presigned = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
					.signatureDuration(presignedUrlExpiration)
					.putObjectRequest(put)
					.build());
		} catch (SdkException e) {
			// 주로 AWS 접근 권한(자격 증명)을 찾지 못한 경우
			log.error("S3 직접 업로드 주소를 만들지 못했습니다: key={}", key, e);
			throw new BusinessException(ErrorCode.UPLOAD_FAILED);
		}

		// host는 브라우저가 알아서 붙이므로 뺀다. 나머지는 클라이언트가 그대로 보내야 서명이 맞는다.
		Map<String, String> headers = new LinkedHashMap<>();
		presigned.signedHeaders().forEach((name, values) -> {
			if (!"host".equalsIgnoreCase(name)) {
				headers.put(name, String.join(",", values));
			}
		});
		return new PresignedUpload(presigned.url().toString(), key, headers, presigned.expiration());
	}

	@Override
	public UploadedObject inspect(String key, int headBytes) {
		if (!OWN_KEY.matcher(key).matches()) {
			throw new BusinessException(ErrorCode.UPLOAD_NOT_FOUND);
		}
		try {
			long size = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
					.contentLength();
			if (size == 0) {
				return new UploadedObject(0, new byte[0]);
			}
			byte[] head = s3Client.getObjectAsBytes(GetObjectRequest.builder()
					.bucket(bucket).key(key).range("bytes=0-" + (headBytes - 1)).build()).asByteArray();
			return new UploadedObject(size, head);
		} catch (S3Exception e) {
			if (e.statusCode() == 404) {
				throw new BusinessException(ErrorCode.UPLOAD_NOT_FOUND);
			}
			log.error("S3에 올린 파일을 확인하지 못했습니다: key={}", key, e);
			throw new BusinessException(ErrorCode.UPLOAD_FAILED);
		} catch (SdkException e) {
			log.error("S3에 올린 파일을 확인하지 못했습니다: key={}", key, e);
			throw new BusinessException(ErrorCode.UPLOAD_FAILED);
		}
	}

	@Override
	public String publicUrl(String key) {
		return cdnBaseUrl + "/" + key;
	}

	@Override
	public boolean isStoredUrl(String url) {
		return url.startsWith(cdnBaseUrl + "/") && OWN_KEY.matcher(url.substring(cdnBaseUrl.length() + 1)).matches();
	}

	@Override
	public String keyOf(String url) {
		String path;
		try {
			path = URI.create(url).getPath();
		} catch (IllegalArgumentException e) {
			return null;
		}
		if (path == null || !path.startsWith(cdnBasePath + "/")) {
			return null;
		}
		String key = path.substring(cdnBasePath.length() + 1);
		return OWN_KEY.matcher(key).matches() ? key : null;
	}

	@Override
	public void forEachKeyStoredBefore(Instant cutoff, Consumer<List<String>> batch) {
		ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucket).prefix(PREFIX).build();
		s3Client.listObjectsV2Paginator(request).forEach(page -> batch.accept(page.contents().stream()
				.filter(object -> object.lastModified().isBefore(cutoff))
				.map(S3Object::key)
				.filter(key -> OWN_KEY.matcher(key).matches())
				.toList()));
	}

	@Override
	public void delete(String key) {
		if (!OWN_KEY.matcher(key).matches()) {
			return;
		}
		try {
			s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
		} catch (SdkException e) {
			log.warn("S3 파일을 지우지 못했습니다: key={}", key, e);
		}
	}

	/** 저장 위치는 서버가 정한다. 클라이언트가 보낸 파일 이름은 쓰지 않는다. (예: products/2026/10/{UUID}.png) */
	private String newKey(String extension) {
		return PREFIX + MONTH.format(LocalDate.now()) + "/" + UUID.randomUUID() + "." + extension;
	}
}
