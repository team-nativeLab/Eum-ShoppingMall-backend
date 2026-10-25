package com.example.shopping.domain.upload.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.shopping.global.scheduling.SchedulerLockService;

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
	private final SchedulerLockService schedulerLockService;
	private final Duration minAge;
	private final int maxDeletesPerRun;

	public UnusedImageCleaner(FileStorage fileStorage, List<ImageReferences> imageReferences,
			SchedulerLockService schedulerLockService,
			@Value("${upload.cleanup.min-age-hours:24}") long minAgeHours,
			@Value("${upload.cleanup.max-deletes-per-run:1000}") int maxDeletesPerRun) {
		this.fileStorage = fileStorage;
		this.imageReferences = imageReferences;
		this.schedulerLockService = schedulerLockService;
		this.minAge = Duration.ofHours(minAgeHours);
		this.maxDeletesPerRun = maxDeletesPerRun;
	}

	@Scheduled(cron = "${upload.cleanup.cron:0 30 4 * * *}")
	public void purgeUnusedImages() {
		Optional<String> owner = schedulerLockService.tryAcquire(JOB_NAME, LOCK_DURATION);
		if (owner.isEmpty()) {
			log.debug("정기 작업 {} 생략: 다른 서버가 실행 중입니다.", JOB_NAME);
			return;
		}
		long startedAt = System.nanoTime();
		try {
			int deleted = purge(Instant.now().minus(minAge));
			log.info("정기 작업 {} 완료: 삭제 {}, 소요 {}ms", JOB_NAME, deleted,
					Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
		} catch (RuntimeException e) {
			log.error("정기 작업 {} 실행 실패", JOB_NAME, e);
		} finally {
			if (!schedulerLockService.release(JOB_NAME, owner.get())) {
				log.error("정기 작업 {} 잠금이 만료되었거나 소유권을 잃었습니다.", JOB_NAME);
			}
		}
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
