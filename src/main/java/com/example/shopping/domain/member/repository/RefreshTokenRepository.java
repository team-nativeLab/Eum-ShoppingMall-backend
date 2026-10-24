package com.example.shopping.domain.member.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.member.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/** 아직 쓰지 않은 토큰만 사용 처리한다. 동시에 같은 토큰이 들어오면 한 요청만 1을 돌려받는다. */
	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update RefreshToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
	int markUsed(@Param("id") Long id, @Param("now") LocalDateTime now);

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from RefreshToken t where t.tokenHash = :tokenHash")
	int deleteByTokenHash(@Param("tokenHash") String tokenHash);

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from RefreshToken t where t.memberId = :memberId")
	int deleteAllByMemberId(@Param("memberId") Long memberId);

	@Query("select t.id from RefreshToken t where t.expiresAt < :now order by t.expiresAt asc, t.id asc")
	List<Long> findExpiredIds(@Param("now") LocalDateTime now, Pageable pageable);

	@Query("select t.id from RefreshToken t where t.usedAt < :usedBefore order by t.usedAt asc, t.id asc")
	List<Long> findUsedIdsBefore(@Param("usedBefore") LocalDateTime usedBefore, Pageable pageable);

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from RefreshToken t where t.id in :ids")
	int deleteBatchByIds(@Param("ids") Collection<Long> ids);
}
