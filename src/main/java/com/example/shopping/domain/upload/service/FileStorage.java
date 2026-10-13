package com.example.shopping.domain.upload.service;

/** 업로드한 파일을 보관하는 곳. 저장소를 바꿀 때(예: S3)는 이 인터페이스의 구현만 교체한다. */
public interface FileStorage {

	/**
	 * @param extension 점을 뺀 확장자 (예: png)
	 * @return 저장된 파일에 접근할 수 있는 주소
	 */
	String store(byte[] content, String extension);
}
