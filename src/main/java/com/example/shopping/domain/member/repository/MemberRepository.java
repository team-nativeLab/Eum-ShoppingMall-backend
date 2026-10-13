package com.example.shopping.domain.member.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberRole;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByEmail(String email);

	boolean existsByEmail(String email);

	/** 검색어는 선택 조건이며 이메일·이름에서 찾는다. */
	@Query("""
			select m from Member m
			where :keyword is null
			   or m.email like concat('%', :keyword, '%')
			   or m.name like concat('%', :keyword, '%')
			""")
	Page<Member> search(@Param("keyword") String keyword, Pageable pageable);

	long countByRoleAndCreatedAtGreaterThanEqual(MemberRole role, LocalDateTime from);

	/**
	 * 로그인 실패를 1회 기록하고, 누적이 maxFailures에 이르면 lockUntil까지 잠근다.
	 * 동시에 들어온 실패가 서로 덮어쓰지 않도록 DB에서 바로 증가시킨다.
	 * (MySQL은 SET을 왼쪽부터 차례로 적용하므로 잠금 판정을 횟수 증가보다 먼저 둔다.)
	 */
	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Member m
			set m.lockedUntil = case when m.failedLoginCount + 1 >= :maxFailures then :lockUntil
			                         else m.lockedUntil end,
			    m.failedLoginCount = m.failedLoginCount + 1
			where m.id = :id
			""")
	int recordLoginFailure(@Param("id") Long id, @Param("maxFailures") int maxFailures,
			@Param("lockUntil") LocalDateTime lockUntil);

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Member m set m.failedLoginCount = 0, m.lockedUntil = null where m.id = :id")
	int resetLoginFailures(@Param("id") Long id);
}
