package com.example.shopping.domain.member.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberRole;

import jakarta.persistence.LockModeType;

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

	/** 한 회원의 동시 요청(장바구니 담기 등)을 한 줄로 세운다. 다른 회원의 요청은 기다리지 않는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Member m where m.id = :id")
	Optional<Member> findByIdForUpdate(@Param("id") Long id);
}
