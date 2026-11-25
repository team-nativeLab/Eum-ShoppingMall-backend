package com.example.shopping.domain.support.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.support.entity.Notice;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

	/** 고정 공지 먼저, 그 안에서는 정렬 순서, 같으면 최신순 */
	Page<Notice> findByVisibleTrueOrderByPinnedDescSortOrderAscCreatedAtDesc(Pageable pageable);

	Optional<Notice> findByIdAndVisibleTrue(Long id);

	/** 관리자용: 비공개 공지까지 전부(순서는 고객 목록과 같다) */
	Page<Notice> findAllByOrderByPinnedDescSortOrderAscCreatedAtDesc(Pageable pageable);
}
