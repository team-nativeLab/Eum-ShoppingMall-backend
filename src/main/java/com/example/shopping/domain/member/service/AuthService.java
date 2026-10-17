package com.example.shopping.domain.member.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.shopping.domain.member.dto.AuthTokens;
import com.example.shopping.domain.member.dto.LoginRequest;
import com.example.shopping.domain.member.dto.SignupRequest;
import com.example.shopping.domain.member.dto.SignupResponse;
import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberRole;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.domain.member.entity.RefreshToken;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.security.JwtTokenProvider;

/**
 * 비밀번호 해시 계산이 느리므로 그동안 DB 커넥션을 잡고 있지 않도록
 * 메서드 전체를 트랜잭션으로 묶지 않고 리포지토리 호출 단위로만 DB에 접근한다.
 */
@Service
public class AuthService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenService refreshTokenService;
	private final AccessTokenService accessTokenService;

	// 없는 이메일로 로그인해도 해시 비교를 한 번 수행해, 응답 시간으로 가입 여부를 추측하지 못하게 한다.
	private final String dummyPasswordHash;

	public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
			JwtTokenProvider jwtTokenProvider, RefreshTokenService refreshTokenService,
			AccessTokenService accessTokenService) {
		this.memberRepository = memberRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
		this.refreshTokenService = refreshTokenService;
		this.accessTokenService = accessTokenService;
		this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	public SignupResponse signup(SignupRequest request) {
		String email = normalizeEmail(request.email());
		if (memberRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}

		String phone = request.phone() == null ? null : request.phone().replace("-", "");
		Member member = Member.create(email, passwordEncoder.encode(request.password()), request.name().trim(), phone);
		try {
			return SignupResponse.from(memberRepository.saveAndFlush(member));
		} catch (DataIntegrityViolationException e) {
			// 같은 이메일로 동시에 가입 요청이 들어오면 위의 중복 검사를 둘 다 통과하므로 유니크 제약이 최종 방어선이다.
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
	}

	public AuthTokens login(LoginRequest request) {
		return issueTokens(authenticate(request));
	}

	/** 관리자 로그인. 일반 회원 계정이면 계정이 없는 것과 똑같이 응답한다. */
	public AuthTokens adminLogin(LoginRequest request) {
		Member member = authenticate(request);
		if (member.getRole() != MemberRole.ADMIN) {
			throw new BusinessException(ErrorCode.LOGIN_FAILED);
		}
		return issueTokens(member);
	}

	/** Refresh 토큰으로 Access 토큰을 다시 발급한다. Refresh 토큰도 새것으로 교체된다. */
	public AuthTokens refresh(String refreshToken) {
		RefreshToken consumed = refreshTokenService.consume(refreshToken);
		Member member = memberRepository.findById(consumed.getMemberId())
				.filter(m -> m.getStatus() == MemberStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		if (consumed.getTokenVersion() != member.getAccessTokenVersion()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		return issueTokens(member);
	}

	/** Refresh 토큰과 요청에 포함된 유효한 Access 토큰을 폐기한다. */
	public void logout(String refreshToken, String authorizationHeader) {
		accessTokenService.revokeIfValid(authorizationHeader);
		refreshTokenService.revoke(refreshToken);
	}

	private Member authenticate(LoginRequest request) {
		Member member = memberRepository.findByEmail(normalizeEmail(request.email())).orElse(null);

		String passwordHash = member == null ? dummyPasswordHash : member.getPasswordHash();
		boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);

		// 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분해서 알려주지 않는다.
		if (member == null || member.getStatus() == MemberStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.LOGIN_FAILED);
		}
		if (!passwordMatches) {
			throw new BusinessException(ErrorCode.LOGIN_FAILED);
		}
		// 계정 상태는 본인 확인(비밀번호 일치)이 끝난 뒤에만 알려준다.
		if (member.getStatus() == MemberStatus.SUSPENDED) {
			throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
		}
		return member;
	}

	private AuthTokens issueTokens(Member member) {
		String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getRole().name(),
				member.getAccessTokenVersion());
		String refreshToken = refreshTokenService.issue(member.getId(), member.getAccessTokenVersion());
		return new AuthTokens(accessToken, jwtTokenProvider.accessTokenExpiry(), refreshToken);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
