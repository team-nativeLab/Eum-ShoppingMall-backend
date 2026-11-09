package com.example.shopping.domain.member.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.member.entity.AdminMfa;

public interface AdminMfaRepository extends JpaRepository<AdminMfa, Long> {

	/**
	 * 코드를 받은 칸을 기록하고, 처음이면 등록을 마친다. 이보다 앞선 칸의 코드만 받은 적이 있고 비밀 키가 그대로일 때만 바꾼다.
	 * 같은 코드가 동시에 두 번 들어와도 한 요청만 1을 돌려받는다.
	 */
	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update AdminMfa m set m.lastUsedStep = :step, m.enabledAt = coalesce(m.enabledAt, :now), m.updatedAt = :now
			where m.memberId = :memberId and m.lastUsedStep < :step and m.secretCiphertext = :secretCiphertext
			""")
	int markUsed(@Param("memberId") Long memberId, @Param("step") long step,
			@Param("secretCiphertext") String secretCiphertext, @Param("now") LocalDateTime now);

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from AdminMfa m where m.memberId = :memberId")
	int deleteByMemberId(@Param("memberId") Long memberId);
}
