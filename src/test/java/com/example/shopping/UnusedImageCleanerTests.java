package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.upload.service.FileStorage;
import com.example.shopping.domain.upload.service.ImageReferences;
import com.example.shopping.domain.upload.service.LocalFileStorage;
import com.example.shopping.domain.upload.service.UnusedImageCleaner;
import com.example.shopping.global.scheduling.ScheduledJobRunner;
import com.example.shopping.global.scheduling.SchedulerLockService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** 참조 없는 업로드 이미지 정리. 다른 테스트가 올린 파일과 섞이지 않게 임시 폴더의 로컬 저장소로 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UnusedImageCleanerTests {

	private static final Instant LONG_AGO = Instant.now().minus(Duration.ofDays(3));

	@Autowired
	private List<ImageReferences> imageReferences;

	@Autowired
	private SchedulerLockService schedulerLockService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@TempDir
	private Path uploadDir;

	private UnusedImageCleaner cleaner(int maxDeletesPerRun) throws IOException {
		FileStorage storage = new LocalFileStorage(uploadDir.toString());
		return new UnusedImageCleaner(storage, imageReferences,
				new ScheduledJobRunner(schedulerLockService, new SimpleMeterRegistry()), 24, maxDeletesPerRun);
	}

	@Test
	void 상품과_배너가_쓰지_않는_오래된_업로드_이미지만_지운다() throws Exception {
		String productImage = file(LONG_AGO);
		String deletedProductImage = file(LONG_AGO);
		String bannerImage = file(LONG_AGO);
		String orphan = file(LONG_AGO);
		String justUploaded = file(Instant.now());
		Path notUpload = uploadDir.resolve("readme.txt");
		Files.writeString(notUpload, "keep");
		Files.setLastModifiedTime(notUpload, FileTime.from(LONG_AGO));

		insertProduct("/files/" + productImage, "ON_SALE");
		// 삭제 처리된 상품도 행이 남아 있으므로 이미지를 지우지 않는다.
		insertProduct("/files/" + deletedProductImage, "DELETED");
		jdbcTemplate.update("""
				insert into banner (image_url, sort_order, visible, created_at, updated_at)
				values (?, 0, true, current_timestamp, current_timestamp)
				""", "/files/" + bannerImage);

		int deleted = cleaner(1000).purge(Instant.now().minus(Duration.ofHours(24)));

		assertThat(deleted).isEqualTo(1);
		assertThat(uploadDir.resolve(orphan)).doesNotExist();
		assertThat(uploadDir.resolve(productImage)).exists();
		assertThat(uploadDir.resolve(deletedProductImage)).exists();
		assertThat(uploadDir.resolve(bannerImage)).exists();
		assertThat(uploadDir.resolve(justUploaded)).exists();
		assertThat(notUpload).exists();
	}

	@Test
	void 한_번에_지우는_수를_넘기면_나머지는_다음_실행에서_지운다() throws Exception {
		String first = file(LONG_AGO);
		String second = file(LONG_AGO);
		UnusedImageCleaner limited = cleaner(1);

		assertThat(limited.purge(Instant.now())).isEqualTo(1);
		assertThat(Files.exists(uploadDir.resolve(first)) ^ Files.exists(uploadDir.resolve(second))).isTrue();
		assertThat(limited.purge(Instant.now())).isEqualTo(1);
		assertThat(uploadDir.resolve(first)).doesNotExist();
		assertThat(uploadDir.resolve(second)).doesNotExist();
	}

	private String file(Instant modifiedAt) throws IOException {
		String name = UUID.randomUUID() + ".png";
		Path path = uploadDir.resolve(name);
		Files.write(path, new byte[] { (byte) 0x89, 'P', 'N', 'G' });
		Files.setLastModifiedTime(path, FileTime.from(modifiedAt));
		return name;
	}

	private void insertProduct(String imageUrl, String status) {
		jdbcTemplate.update("""
				insert into product (name, image_url, base_price, price_visible, status, created_at, updated_at)
				values ('안전모', ?, 1000, true, ?, current_timestamp, current_timestamp)
				""", imageUrl, status);
	}
}
