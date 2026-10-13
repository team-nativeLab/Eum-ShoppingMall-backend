package com.example.shopping.domain.support.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.support.entity.Notice;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

	/** 고정 공지 먼저, 그 안에서는 최신순 */
	Page<Notice> findByVisibleTrueOrderByPinnedDescCreatedAtDesc(Pageable pageable);

	Optional<Notice> findByIdAndVisibleTrue(Long id);

	/** 관리자용: 비공개 공지까지 전부 */
	Page<Notice> findAllByOrderByPinnedDescCreatedAtDesc(Pageable pageable);
}
