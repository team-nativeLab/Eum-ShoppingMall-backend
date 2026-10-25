package com.example.shopping.domain.upload.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3StorageConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(S3StorageConfiguration.class)
			.withPropertyValues("upload.storage=s3", "upload.s3.region=ap-northeast-2");

	@Test
	void 설정한_액세스_키로_S3에_접근한다() {
		runner.withPropertyValues("upload.s3.access-key=AKIATEST", "upload.s3.secret-key=test-secret")
				.run(context -> {
					assertThat(context).hasSingleBean(S3Client.class).hasSingleBean(S3Presigner.class);
					AwsCredentials credentials = context.getBean(AwsCredentialsProvider.class).resolveCredentials();
					assertThat(credentials.accessKeyId()).isEqualTo("AKIATEST");
					assertThat(credentials.secretAccessKey()).isEqualTo("test-secret");
				});
	}

	@Test
	void 액세스_키나_시크릿_키가_비어_있으면_서버가_뜨지_않는다() {
		runner.withPropertyValues("upload.s3.access-key=", "upload.s3.secret-key=test-secret")
				.run(context -> assertThat(context).hasFailed().getFailure()
						.rootCause().hasMessageContaining("UPLOAD_S3_ACCESS_KEY"));
		runner.withPropertyValues("upload.s3.access-key=AKIATEST", "upload.s3.secret-key=")
				.run(context -> assertThat(context).hasFailed().getFailure()
						.rootCause().hasMessageContaining("UPLOAD_S3_SECRET_KEY"));
	}
}
