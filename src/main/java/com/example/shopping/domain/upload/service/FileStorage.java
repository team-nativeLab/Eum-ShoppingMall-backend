package com.example.shopping.domain.upload.service;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/** 업로드한 파일을 보관하는 곳. 저장소를 바꿀 때(예: S3)는 이 인터페이스의 구현만 교체한다. */
public interface FileStorage {

	/**
	 * @param extension 점을 뺀 확장자 (예: png)
	 * @return 저장된 파일에 접근할 수 있는 주소
	 */
	String store(byte[] content, String extension);

	/** store가 돌려주는 형식의 주소인지. 상품·배너 이미지 주소는 이 주소만 받는다. */
	boolean isStoredUrl(String url);

	/**
	 * 주소에서 저장 위치(키)를 뽑는다. 이 저장소가 만드는 형식이 아니면 null.
	 * 주소의 호스트는 보지 않는다. CDN 주소가 바뀌어도 예전 주소가 가리키는 파일을 찾을 수 있어야 정리 작업이 쓰는 파일을 지우지 않는다.
	 */
	String keyOf(String url);

	/** cutoff보다 먼저 저장된 파일의 키를 묶음으로 넘긴다. */
	void forEachKeyStoredBefore(Instant cutoff, Consumer<List<String>> batch);

	/** 이 저장소가 만드는 형식의 키가 아니면 아무것도 하지 않는다. */
	void delete(String key);
}
