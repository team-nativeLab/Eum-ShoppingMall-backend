package com.example.shopping.domain.upload.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 서버의 로컬 디스크(upload.dir)에 저장하고 /files/** 경로로 내려준다.
 * 서버가 여러 대이거나 재배포 때 디스크가 초기화되는 환경에서는 쓸 수 없으므로, 운영에서는 S3 같은 외부 저장소 구현으로 바꿔야 한다.
 */
@Component
public class LocalFileStorage implements FileStorage, WebMvcConfigurer {

	private static final String PUBLIC_PATH = "/files";

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
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler(PUBLIC_PATH + "/**")
				.addResourceLocations(root.toUri().toString())
				.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
	}
}
