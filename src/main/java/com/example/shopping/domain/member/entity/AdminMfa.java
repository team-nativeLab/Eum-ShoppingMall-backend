package com.example.shopping.domain.member.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자 OTP(TOTP) 비밀 키(#164). 서버가 코드를 계산하려면 원래 값이 필요하므로 해시가 아니라 서버 키로 암호화해 둔다(SecretCipher).
 * 등록을 시작하면 행이 생기고, 처음 맞는 코드를 넣으면 등록이 끝난다(enabledAt).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminMfa extends BaseTimeEntity {

	@Id
	@Column(name = "member_id")
	private Long memberId;

	@Column(nullable = false)
	private String secretCiphertext;

	// 등록을 마친 시각. 아직이면 null
	private LocalDateTime enabledAt;

	// 마지막으로 받은 코드의 30초 칸 번호. 같은 칸이나 그 이전 칸의 코드는 다시 받지 않는다(재사용 방지).
	@ColumnDefault("0")
	@Column(nullable = false)
	private long lastUsedStep;

	public static AdminMfa pending(Long memberId, String secretCiphertext) {
		AdminMfa mfa = new AdminMfa();
		mfa.memberId = memberId;
		mfa.secretCiphertext = secretCiphertext;
		return mfa;
	}

	public boolean isEnabled() {
		return enabledAt != null;
	}

	/** 등록을 마치기 전에만 비밀 키를 새로 만든다. */
	public void replacePendingSecret(String secretCiphertext) {
		if (isEnabled()) {
			throw new IllegalStateException("이미 등록한 OTP는 바꿀 수 없습니다.");
		}
		this.secretCiphertext = secretCiphertext;
		this.lastUsedStep = 0;
	}
}
