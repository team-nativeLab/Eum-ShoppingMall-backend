package com.example.shopping.domain.audit.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
