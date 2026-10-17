package com.example.shopping.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

class S3FileStorageTest {

	@Test
	void S3에_불변_캐시와_MIME을_설정하고_CDN_주소를_반환한다() {
		S3Client client = mock(S3Client.class);
		S3FileStorage storage = new S3FileStorage(client, "private-image-bucket", "https://cdn.example.com/");

		String url = storage.store(new byte[] {1, 2, 3}, "webp");

		ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(client).putObject(request.capture(), any(RequestBody.class));
		assertThat(request.getValue().bucket()).isEqualTo("private-image-bucket");
		assertThat(request.getValue().key()).matches("products/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.webp");
		assertThat(request.getValue().contentType()).isEqualTo("image/webp");
		assertThat(request.getValue().cacheControl()).isEqualTo("public, max-age=31536000, immutable");
		assertThat(url).isEqualTo("https://cdn.example.com/" + request.getValue().key());
	}
}
