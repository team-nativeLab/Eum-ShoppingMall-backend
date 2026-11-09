package com.example.shopping.domain.member.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.member.entity.AdminLoginChallenge;

public interface AdminLoginChallengeRepository extends JpaRepository<AdminLoginChallenge, Long> {

	Optional<AdminLoginChallenge> findByTokenHash(String tokenHash);

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update AdminLoginChallenge c set c.failedAttempts = c.failedAttempts + 1 where c.id = :id")
	int recordFailure(@Param("id") Long id);

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from AdminLoginChallenge c where c.id = :id")
	int deleteChallenge(@Param("id") Long id);

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from AdminLoginChallenge c where c.memberId = :memberId")
	int deleteAllByMemberId(@Param("memberId") Long memberId);

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from AdminLoginChallenge c where c.expiresAt < :now")
	int deleteExpired(@Param("now") LocalDateTime now);
}
