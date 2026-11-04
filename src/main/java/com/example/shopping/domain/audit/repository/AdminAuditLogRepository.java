package com.example.shopping.domain.audit.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.audit.entity.AdminAuditLog;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

	@Query("""
			select a from AdminAuditLog a
			where (:actorId is null or a.actorMemberId = :actorId)
			  and (:targetType is null or a.targetType = :targetType)
			  and (:fromDate is null or a.createdAt >= :fromDate)
			  and (:toDate is null or a.createdAt < :toDate)
			order by a.createdAt desc, a.id desc
			""")
	Page<AdminAuditLog> search(@Param("actorId") Long actorId, @Param("targetType") String targetType,
			@Param("fromDate") java.time.LocalDateTime fromDate,
			@Param("toDate") java.time.LocalDateTime toDate, Pageable pageable);

	/** 보존 기간이 지난 로그를 오래된 순으로 한 묶음 고른다. created_at 인덱스(V18)를 탄다. */
	@Query("select a.id from AdminAuditLog a where a.createdAt < :cutoff order by a.createdAt asc, a.id asc")
	List<Long> findIdsCreatedBefore(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from AdminAuditLog a where a.id in :ids")
	int deleteBatchByIds(@Param("ids") Collection<Long> ids);
}
