package com.example.shopping.domain.notification.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shopping.domain.notification.entity.NotificationMessage;
import com.example.shopping.domain.notification.entity.NotificationStatus;

public interface NotificationMessageRepository extends JpaRepository<NotificationMessage, Long> {

	/** 보낼 차례가 된 알림 */
	@Query("""
			select m.id from NotificationMessage m
			where m.status = com.example.shopping.domain.notification.entity.NotificationStatus.PENDING
			  and m.nextAttemptAt <= :now
			order by m.id
			""")
	List<Long> findDueIds(@Param("now") LocalDateTime now, Pageable pageable);

	/** 이 서버가 보내겠다고 표시한다. 1을 돌려줘야 보낸다. 다른 서버가 먼저 가져갔으면 0이다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update NotificationMessage m set m.status = :sending, m.updatedAt = :now
			where m.id = :id and m.status = :pending
			""")
	int claim(@Param("id") Long id, @Param("now") LocalDateTime now,
			@Param("pending") NotificationStatus pending, @Param("sending") NotificationStatus sending);

	/** 보내던 서버가 중간에 내려가 SENDING에 멈춘 알림을 다시 보낼 수 있게 되돌린다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update NotificationMessage m set m.status = :pending
			where m.status = :sending and m.updatedAt < :before
			""")
	int releaseStuck(@Param("before") LocalDateTime before, @Param("pending") NotificationStatus pending,
			@Param("sending") NotificationStatus sending);

	List<NotificationMessage> findByReferenceTypeAndReferenceIdOrderByIdDesc(String referenceType, Long referenceId);
}
