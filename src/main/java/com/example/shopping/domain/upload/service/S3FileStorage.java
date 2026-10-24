package com.example.shopping.domain.upload.service;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Uploads private objects to S3 and returns their public CloudFront URL. */
@Component
@ConditionalOnProperty(name = "upload.storage", havingValue = "s3")
public class S3FileStorage implements FileStorage {

	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy/MM");
	private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

	private final S3Client s3Client;
	private final String bucket;
	private final String cdnBaseUrl;

	public S3FileStorage(S3Client s3Client, @Value("${upload.s3.bucket}") String bucket,
			@Value("${upload.s3.cdn-base-url}") String cdnBaseUrl) {
		Assert.hasText(bucket, "UPLOAD_S3_BUCKET is required when UPLOAD_STORAGE=s3");
		URI cdnUri = URI.create(cdnBaseUrl);
		Assert.isTrue("https".equalsIgnoreCase(cdnUri.getScheme()) && cdnUri.getHost() != null,
				"UPLOAD_CDN_BASE_URL must be an HTTPS URL");
		this.s3Client = s3Client;
		this.bucket = bucket;
		this.cdnBaseUrl = cdnBaseUrl.replaceAll("/+$", "");
	}

	@Override
	public String store(byte[] content, String extension) {
		String key = "products/" + MONTH.format(LocalDate.now()) + "/" + UUID.randomUUID() + "." + extension;
		s3Client.putObject(PutObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.contentType(contentType(extension))
				.cacheControl(CACHE_CONTROL)
				.build(), RequestBody.fromBytes(content));
		return cdnBaseUrl + "/" + key;
	}

	private String contentType(String extension) {
		return switch (extension.toLowerCase()) {
			case "jpg", "jpeg" -> "image/jpeg";
			case "png" -> "image/png";
			case "gif" -> "image/gif";
			case "webp" -> "image/webp";
			default -> throw new IllegalArgumentException("Unsupported image extension");
		};
	}
}
