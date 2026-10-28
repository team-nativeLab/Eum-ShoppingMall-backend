package com.example.shopping.domain.member.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shopping.domain.member.entity.EmailVerification;
import com.example.shopping.domain.member.entity.VerificationPurpose;

import jakarta.persistence.LockModeType;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

	/** 재발송 간격 확인용 */
	Optional<EmailVerification> findFirstByEmailAndPurposeOrderByIdDesc(String email, VerificationPurpose purpose);

	/** 하루 발송 횟수 확인용. 기록은 하루 동안 남는다(EmailVerificationCleaner). */
	long countByEmailAndPurposeAndCreatedAtAfter(String email, VerificationPurpose purpose, LocalDateTime after);

	/** 하루 동안 틀린 횟수 합계. 새 인증번호를 받아도 이전 번호에서 틀린 횟수가 남는다. */
	@Query("""
			select coalesce(sum(v.attempts), 0L) from EmailVerification v
			where v.email = :email and v.purpose = :purpose and v.createdAt > :after
			""")
	long sumAttemptsSince(@Param("email") String email, @Param("purpose") VerificationPurpose purpose,
			@Param("after") LocalDateTime after);

	/** 가장 최근에 보낸, 아직 쓰지 않은 인증번호. 틀린 횟수를 정확히 세려고 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<EmailVerification> findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc(String email,
			VerificationPurpose purpose);

	/** 같은 토큰으로 두 번 가입하지 못하게 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<EmailVerification> findByTokenHashAndPurpose(String tokenHash, VerificationPurpose purpose);

	/** 새 인증번호를 보내면 이전 인증번호는 쓸 수 없게 한다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update EmailVerification v set v.consumedAt = :now
			where v.email = :email and v.purpose = :purpose and v.consumedAt is null
			""")
	int consumeAll(@Param("email") String email, @Param("purpose") VerificationPurpose purpose,
			@Param("now") LocalDateTime now);

	@Modifying
	@Query("delete from EmailVerification v where v.createdAt < :before")
	int deleteCreatedBefore(@Param("before") LocalDateTime before);
}
