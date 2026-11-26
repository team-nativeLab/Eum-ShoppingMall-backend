package com.example.shopping.domain.member.entity;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원에 연결한 카카오·구글 계정(#225). 회원 하나에 제공자마다 하나만 연결한다. */
@Entity
@Table(name = "member_social_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberSocialAccount extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long memberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SocialProvider provider;

	// 카카오 회원번호, 구글 sub
	@Column(nullable = false, length = 100)
	private String providerUserId;

	// 연결할 때 제공자가 알려 준 이메일
	@Column(length = 255)
	private String email;

	public static MemberSocialAccount link(Long memberId, SocialProvider provider, String providerUserId,
			String email) {
		MemberSocialAccount account = new MemberSocialAccount();
		account.memberId = memberId;
		account.provider = provider;
		account.providerUserId = providerUserId;
		account.email = email;
		return account;
	}
}
