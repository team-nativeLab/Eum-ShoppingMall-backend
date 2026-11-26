package com.example.shopping.domain.member.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shopping.domain.member.entity.SocialSignupTicket;

public interface SocialSignupTicketRepository extends JpaRepository<SocialSignupTicket, Long> {

	Optional<SocialSignupTicket> findByTokenHash(String tokenHash);

	/** 한 번만 쓰도록 지운다. 동시에 두 번 들어오면 한쪽만 1을 받는다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from SocialSignupTicket t where t.id = :id")
	int deleteOnce(@Param("id") Long id);

	/** 지난 가입 대기를 지운다(정기 작업). 제공자가 알려 준 이메일·이름이 들어 있어 오래 두지 않는다. */
	@Modifying
	@Query("delete from SocialSignupTicket t where t.expiresAt <= :now")
	int deleteExpired(@Param("now") LocalDateTime now);
}
