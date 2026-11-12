package com.example.shopping.domain.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.member.entity.MemberAddress;

public interface MemberAddressRepository extends JpaRepository<MemberAddress, Long> {

	/** 기본 배송지를 먼저, 나머지는 최근에 고친 순서로 */
	@Query("""
			select a from MemberAddress a where a.memberId = :memberId
			order by a.defaultAddress desc, a.updatedAt desc, a.id desc
			""")
	List<MemberAddress> findAllOfMember(@Param("memberId") Long memberId);

	Optional<MemberAddress> findByIdAndMemberId(Long id, Long memberId);

	long countByMemberId(Long memberId);

	/** 기본 배송지를 하나로 맞출 때 나머지의 기본 표시를 지운다. */
	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update MemberAddress a set a.defaultAddress = false where a.memberId = :memberId and a.id <> :keepId")
	int clearDefaultExcept(@Param("memberId") Long memberId, @Param("keepId") Long keepId);

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from MemberAddress a where a.memberId = :memberId")
	int deleteAllByMemberId(@Param("memberId") Long memberId);
}
