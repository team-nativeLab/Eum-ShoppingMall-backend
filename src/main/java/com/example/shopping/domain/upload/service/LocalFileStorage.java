package com.example.shopping.domain.upload.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.extern.slf4j.Slf4j;

/**
 * 서버의 로컬 디스크(upload.dir)에 저장하고 /files/** 경로로 내려준다.
 * 서버가 여러 대이거나 재배포 때 디스크가 초기화되는 환경에서는 쓸 수 없으므로, 운영에서는 S3 같은 외부 저장소 구현으로 바꿔야 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "upload.storage", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage, WebMvcConfigurer {

	private static final String PUBLIC_PATH = "/files";
	// 경로 조작(../)이 들어갈 수 없는 파일 이름만 다룬다.
	private static final Pattern OWN_KEY = Pattern.compile("^[A-Za-z0-9_-]+\\.(jpg|png|gif|webp)$");

	private final Path root;

	public LocalFileStorage(@Value("${upload.dir}") String dir) throws IOException {
		this.root = Path.of(dir).toAbsolutePath().normalize();
		Files.createDirectories(root);
	}

	@Override
	public String store(byte[] content, String extension) {
		// 파일명은 서버가 정한다. 클라이언트가 보낸 파일명은 경로 조작에 쓰일 수 있어 사용하지 않는다.
		String filename = UUID.randomUUID() + "." + extension;
		try {
			Files.write(root.resolve(filename), content);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return PUBLIC_PATH + "/" + filename;
	}

	@Override
	public boolean isStoredUrl(String url) {
		return keyOf(url) != null;
	}

	@Override
	public String keyOf(String url) {
		if (!url.startsWith(PUBLIC_PATH + "/")) {
			return null;
		}
		String key = url.substring(PUBLIC_PATH.length() + 1);
		return OWN_KEY.matcher(key).matches() ? key : null;
	}

	@Override
	public void forEachKeyStoredBefore(Instant cutoff, Consumer<List<String>> batch) {
		try (Stream<Path> files = Files.list(root)) {
			batch.accept(files
					.filter(file -> OWN_KEY.matcher(file.getFileName().toString()).matches())
					.filter(file -> modifiedBefore(file, cutoff))
					.map(file -> file.getFileName().toString())
					.toList());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void delete(String key) {
		if (!OWN_KEY.matcher(key).matches()) {
			return;
		}
		try {
			Files.deleteIfExists(root.resolve(key));
		} catch (IOException e) {
			log.warn("업로드 파일을 지우지 못했습니다: {}", key, e);
		}
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler(PUBLIC_PATH + "/**")
				.addResourceLocations(root.toUri().toString())
				.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
	}

	private boolean modifiedBefore(Path file, Instant cutoff) {
		try {
			return Files.getLastModifiedTime(file).toInstant().isBefore(cutoff);
		} catch (IOException e) {
			return false;
		}
	}
}
