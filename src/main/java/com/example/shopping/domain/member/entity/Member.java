package com.example.shopping.domain.member.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.util.StringUtils;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false, length = 100)
	private String passwordHash;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(length = 30)
	private String phone;

	// enum은 MySQL ENUM 타입 대신 VARCHAR로 저장한다 (값 추가 시 컬럼 변경이 필요 없도록).
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private MemberRole role = MemberRole.USER;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private MemberStatus status = MemberStatus.ACTIVE;

	private LocalDateTime withdrawnAt;

	@Column(length = 200)
	private String suspensionReason;

	/** Persisted generation used to invalidate access and refresh tokens after credential changes. */
	@ColumnDefault("0")
	@Column(name = "access_token_version", nullable = false)
	private long accessTokenVersion;

	/** 가입할 때 이메일 인증을 마친 시각. 인증 없이 가입한 계정(기능 도입 전 가입 등)은 비어 있다. */
	private LocalDateTime emailVerifiedAt;

	/** 일반 회원(USER, ACTIVE)으로 생성한다. */
	public static Member create(String email, String passwordHash, String name, String phone) {
		Member member = new Member();
		member.email = email;
		member.passwordHash = passwordHash;
		member.name = name;
		member.phone = phone;
		return member;
	}

	public static Member createAdmin(String email, String passwordHash, String name) {
		Member member = create(email, passwordHash, name, null);
		member.role = MemberRole.ADMIN;
		return member;
	}

	public static Member createSuperAdmin(String email, String passwordHash, String name) {
		Member member = create(email, passwordHash, name, null);
		member.role = MemberRole.SUPER_ADMIN;
		return member;
	}

	public void markEmailVerified(LocalDateTime verifiedAt) {
		this.emailVerifiedAt = verifiedAt;
	}

	public void changeRole(MemberRole role) {
		this.role = role;
	}

	/** null인 항목은 바꾸지 않는다. 연락처는 빈 문자열을 주면 지운다. */
	public void updateProfile(String name, String phone) {
		if (name != null) {
			this.name = name;
		}
		if (phone != null) {
			this.phone = StringUtils.hasText(phone) ? phone : null;
		}
	}

	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
		this.accessTokenVersion++;
	}

	public void withdraw() {
		this.status = MemberStatus.WITHDRAWN;
		this.withdrawnAt = LocalDateTime.now();
		this.email = "withdrawn+" + id + "@invalid.local";
		this.name = "탈퇴한 회원";
		this.phone = null;
		this.passwordHash = "!withdrawn!" + id;
		this.suspensionReason = null;
		this.accessTokenVersion++;
	}

	public void changeStatus(MemberStatus status, String reason) {
		this.status = status;
		this.suspensionReason = status == MemberStatus.SUSPENDED ? reason : null;
	}
}
