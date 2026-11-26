package com.example.shopping.domain.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shopping.domain.member.entity.MemberSocialAccount;
import com.example.shopping.domain.member.entity.SocialProvider;

public interface MemberSocialAccountRepository extends JpaRepository<MemberSocialAccount, Long> {

	Optional<MemberSocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

	Optional<MemberSocialAccount> findByMemberIdAndProvider(Long memberId, SocialProvider provider);

	List<MemberSocialAccount> findByMemberIdOrderByIdAsc(Long memberId);

	long countByMemberId(Long memberId);

	/** 탈퇴한 회원의 연결을 지운다. 같은 카카오·구글 계정으로 다시 가입할 수 있다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from MemberSocialAccount a where a.memberId = :memberId")
	int deleteAllByMemberId(@Param("memberId") Long memberId);
}
