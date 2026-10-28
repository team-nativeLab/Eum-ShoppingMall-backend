package com.example.shopping.domain.upload.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.shopping.global.scheduling.ScheduledJobRunner;

import lombok.extern.slf4j.Slf4j;

/**
 * 상품·배너가 쓰지 않는 업로드 이미지를 지운다. 이미지를 바꾸거나 배너를 지운 뒤 남은 파일,
 * 올리기만 하고 등록하지 않은 파일(직접 업로드를 끝내지 않은 파일 포함)이 대상이다.
 * 올린 직후 아직 상품에 연결하지 않은 파일을 지우지 않도록 min-age보다 오래된 파일만 본다.
 */
@Slf4j
@Component
public class UnusedImageCleaner {

	private static final String JOB_NAME = "purgeUnusedImages";
	private static final Duration LOCK_DURATION = Duration.ofHours(1);

	private final FileStorage fileStorage;
	private final List<ImageReferences> imageReferences;
	private final ScheduledJobRunner jobRunner;
	private final Duration minAge;
	private final int maxDeletesPerRun;

	public UnusedImageCleaner(FileStorage fileStorage, List<ImageReferences> imageReferences,
			ScheduledJobRunner jobRunner,
			@Value("${upload.cleanup.min-age-hours:24}") long minAgeHours,
			@Value("${upload.cleanup.max-deletes-per-run:1000}") int maxDeletesPerRun) {
		this.fileStorage = fileStorage;
		this.imageReferences = imageReferences;
		this.jobRunner = jobRunner;
		this.minAge = Duration.ofHours(minAgeHours);
		this.maxDeletesPerRun = maxDeletesPerRun;
	}

	@Scheduled(cron = "${upload.cleanup.cron:0 30 4 * * *}")
	public void purgeUnusedImages() {
		jobRunner.run(JOB_NAME, LOCK_DURATION, (stats, owner) -> stats.processed(purge(Instant.now().minus(minAge))));
	}

	/**
	 * cutoff 전에 올라왔고 어디에서도 쓰지 않는 파일을 지운다. 설정이나 데이터가 잘못돼 쓰는 파일까지
	 * 지우는 사고를 줄이려고 한 번에 maxDeletesPerRun개까지만 지운다. 남은 파일은 다음 실행에서 지운다.
	 *
	 * @return 지운 파일 수
	 */
	public int purge(Instant cutoff) {
		// 파일 목록보다 참조를 먼저 읽는다. 그 사이 새로 연결된 이미지는 cutoff 뒤에 올라온 파일이라 지울 대상이 아니다.
		Set<String> usedKeys = new HashSet<>();
		for (ImageReferences references : imageReferences) {
			for (String url : references.findAllImageUrls()) {
				String key = fileStorage.keyOf(url);
				if (key != null) {
					usedKeys.add(key);
				}
			}
		}

		AtomicInteger deleted = new AtomicInteger();
		fileStorage.forEachKeyStoredBefore(cutoff, keys -> {
			for (String key : keys) {
				if (deleted.get() >= maxDeletesPerRun) {
					return;
				}
				if (!usedKeys.contains(key)) {
					fileStorage.delete(key);
					deleted.incrementAndGet();
				}
			}
		});
		if (deleted.get() >= maxDeletesPerRun) {
			log.warn("정기 작업 {}: 한 번에 지울 수 있는 {}개를 채워 나머지는 다음 실행에서 지웁니다.", JOB_NAME,
					maxDeletesPerRun);
		}
		return deleted.get();
	}
}
