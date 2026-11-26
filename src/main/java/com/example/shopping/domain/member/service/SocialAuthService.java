package com.example.shopping.domain.member.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.audit.service.AdminAuditLogService;
import com.example.shopping.domain.consent.service.ConsentService;
import com.example.shopping.domain.member.dto.AuthTokens;
import com.example.shopping.domain.member.dto.request.SocialSignupRequest;
import com.example.shopping.domain.member.dto.response.SocialAccountResponse;
import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberSocialAccount;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.domain.member.entity.SocialProvider;
import com.example.shopping.domain.member.entity.SocialSignupTicket;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.domain.member.repository.MemberSocialAccountRepository;
import com.example.shopping.domain.member.social.GoogleIdentityClient;
import com.example.shopping.domain.member.social.KakaoIdentityClient;
import com.example.shopping.domain.member.social.SocialIdentityClient;
import com.example.shopping.domain.member.social.SocialLoginProperties;
import com.example.shopping.domain.member.social.SocialProfile;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.validation.ContactFormats;

/**
 * 카카오·구글 로그인(#225).
 * <ul>
 * <li>연결된 카카오·구글 계정이면 그 회원으로 로그인한다.</li>
 * <li>연결이 없고 같은 이메일의 회원이 있으면, 제공자가 인증한 이메일일 때만 그 회원에 연결하고 로그인한다. 관리자 계정에는 연결하지 않는다.</li>
 * <li>처음 온 사람은 가입용 1회 토큰을 받고, 약관·개인정보 동의를 보내 가입을 끝낸다(가입하면 바로 로그인).</li>
 * </ul>
 * 제공자 확인(외부 호출)은 트랜잭션 밖에서 하고, DB 반영만 짧은 트랜잭션으로 한다.
 */
@Service
public class SocialAuthService {

	private static final Logger log = LoggerFactory.getLogger(SocialAuthService.class);
	private static final String ADMIN_SOCIAL_LOGIN_REJECTED = "관리자 계정 소셜 로그인 시도(거절)";

	private final GoogleIdentityClient googleClient;
	private final KakaoIdentityClient kakaoClient;
	private final SocialLoginProperties properties;
	private final MemberRepository memberRepository;
	private final MemberSocialAccountRepository socialAccountRepository;
	private final SocialSignupTickets signupTickets;
	private final ConsentService consentService;
	private final AuthService authService;
	private final AdminAuditLogService auditLogService;
	private final TransactionTemplate transactionTemplate;

	public SocialAuthService(GoogleIdentityClient googleClient, KakaoIdentityClient kakaoClient,
			SocialLoginProperties properties, MemberRepository memberRepository,
			MemberSocialAccountRepository socialAccountRepository, SocialSignupTickets signupTickets,
			ConsentService consentService, AuthService authService, AdminAuditLogService auditLogService,
			TransactionTemplate transactionTemplate) {
		this.googleClient = googleClient;
		this.kakaoClient = kakaoClient;
		this.properties = properties;
		this.memberRepository = memberRepository;
		this.socialAccountRepository = socialAccountRepository;
		this.signupTickets = signupTickets;
		this.consentService = consentService;
		this.authService = authService;
		this.auditLogService = auditLogService;
		this.transactionTemplate = transactionTemplate;
	}

	/** 웹: 프론트가 받은 인가 코드. redirectUri는 등록한 주소(app.social.redirect-uris) 중 하나여야 한다. */
	public SocialLoginResult loginWithCode(SocialProvider provider, String code, String redirectUri,
			String codeVerifier, String clientIp) {
		if (!properties.isAllowedRedirectUri(redirectUri)) {
			throw new BusinessException(ErrorCode.SOCIAL_REDIRECT_URI_NOT_ALLOWED);
		}
		SocialProfile profile = client(provider).verifyAuthorizationCode(code, redirectUri, codeVerifier);
		return inTransaction(() -> login(profile), clientIp);
	}

	/** 앱: 앱 SDK가 받은 토큰(구글 ID 토큰, 카카오 액세스 토큰) */
	public SocialLoginResult loginWithAppToken(SocialProvider provider, String token, String clientIp) {
		SocialProfile profile = client(provider).verifyAppToken(token);
		return inTransaction(() -> login(profile), clientIp);
	}

	/**
	 * 처음 온 사람의 가입을 끝내고 로그인한다. 토큰은 한 번만 쓴다. 그사이 같은 계정이 연결되거나 같은 이메일로 가입했으면
	 * 로그인과 같은 규칙으로 그 회원을 쓴다.
	 */
	public AuthTokens completeSignup(SocialSignupRequest request, String clientIp) {
		try {
			return inTransaction(() -> {
				SocialSignupTicket ticket = signupTickets.consume(request.signupToken());
				SocialProfile profile = new SocialProfile(ticket.getProvider(), ticket.getProviderUserId(),
						ticket.getEmail(), ticket.isEmailVerified(), ticket.getName());
				Optional<Member> linked = linkedMember(profile);
				if (linked.isPresent()) {
					return tokensFor(linked.get(), ticket.getEmail());
				}
				Optional<Member> sameEmail = memberRepository.findByEmail(ticket.getEmail());
				if (sameEmail.isPresent()) {
					linkExisting(sameEmail.get(), profile, ticket.getEmail());
					return tokensFor(sameEmail.get(), ticket.getEmail());
				}
				Member member = Member.createSocial(ticket.getEmail(), request.name().trim(),
						ContactFormats.phone(request.phone()));
				if (ticket.isEmailVerified()) {
					member.markEmailVerified(LocalDateTime.now());
				}
				Member saved = memberRepository.saveAndFlush(member);
				consentService.recordSignup(saved.getId(), Boolean.TRUE.equals(request.marketingAgreed()));
				socialAccountRepository.saveAndFlush(MemberSocialAccount.link(saved.getId(), profile.provider(),
						profile.providerUserId(), ticket.getEmail()));
				return authService.issueMemberTokens(saved);
			}, clientIp);
		} catch (DataIntegrityViolationException e) {
			// 같은 이메일·같은 계정으로 동시에 가입하면 유니크 제약이 최종 방어선이다.
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
	}

	/** 내 계정에 연결한 카카오·구글. 연결한 순서대로 */
	public List<SocialAccountResponse> accounts(Long memberId) {
		return socialAccountRepository.findByMemberIdOrderByIdAsc(memberId).stream()
				.map(SocialAccountResponse::from)
				.toList();
	}

	/**
	 * 연결을 끊는다. 비밀번호가 없는 계정의 마지막 연결이면 로그인할 방법이 없어지므로 막는다(409).
	 * 카카오는 커밋한 뒤 카카오 쪽 연결도 끊는다.
	 */
	public void unlink(Long memberId, SocialProvider provider) {
		MemberSocialAccount account = inTransaction(() -> {
			Member member = memberRepository.findById(memberId)
					.filter(m -> m.getStatus() == MemberStatus.ACTIVE)
					.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
			MemberSocialAccount linked = socialAccountRepository.findByMemberIdAndProvider(memberId, provider)
					.orElseThrow(() -> new BusinessException(ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED));
			if (!member.hasPassword() && socialAccountRepository.countByMemberId(memberId) <= 1) {
				throw new BusinessException(ErrorCode.SOCIAL_UNLINK_NOT_ALLOWED);
			}
			socialAccountRepository.delete(linked);
			return linked;
		});
		client(provider).unlink(account.getProviderUserId());
	}

	/** 탈퇴: 연결을 모두 지우고, 탈퇴가 커밋되면 제공자 쪽 연결도 끊는다. 탈퇴 트랜잭션 안에서 부른다. */
	public void unlinkAllForWithdrawal(Long memberId) {
		List<MemberSocialAccount> accounts = socialAccountRepository.findByMemberIdOrderByIdAsc(memberId);
		if (accounts.isEmpty()) {
			return;
		}
		socialAccountRepository.deleteAllByMemberId(memberId);
		Runnable unlinkAll = () -> accounts.forEach(account -> client(account.getProvider())
				.unlink(account.getProviderUserId()));
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					unlinkAll.run();
				}
			});
		} else {
			unlinkAll.run();
		}
	}

	private SocialLoginResult login(SocialProfile profile) {
		Optional<Member> linked = linkedMember(profile);
		if (linked.isPresent()) {
			return SocialLoginResult.loggedIn(tokensFor(linked.get(), profile.email()));
		}
		if (!StringUtils.hasText(profile.email())) {
			throw new BusinessException(ErrorCode.SOCIAL_EMAIL_REQUIRED);
		}
		String email = ContactFormats.loginEmail(profile.email());
		Optional<Member> sameEmail = memberRepository.findByEmail(email);
		if (sameEmail.isPresent()) {
			linkExisting(sameEmail.get(), profile, email);
			return SocialLoginResult.loggedIn(tokensFor(sameEmail.get(), email));
		}
		return SocialLoginResult.signupRequired(signupTickets.issue(profile, email), profile.provider(), email,
				profile.name());
	}

	/** 연결된 회원. 탈퇴한 회원의 연결이 남아 있으면 지우고 없는 것으로 본다. */
	private Optional<Member> linkedMember(SocialProfile profile) {
		Optional<MemberSocialAccount> account = socialAccountRepository
				.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId());
		if (account.isEmpty()) {
			return Optional.empty();
		}
		Optional<Member> member = memberRepository.findById(account.get().getMemberId())
				.filter(m -> m.getStatus() != MemberStatus.WITHDRAWN);
		if (member.isEmpty()) {
			socialAccountRepository.delete(account.get());
		}
		return member;
	}

	/** 같은 이메일의 기존 회원에 연결한다. 제공자가 인증한 이메일만 받고, 관리자 계정에는 연결하지 않는다. */
	private void linkExisting(Member member, SocialProfile profile, String email) {
		rejectAdmin(member, email);
		if (!profile.emailVerified()) {
			throw new BusinessException(ErrorCode.SOCIAL_EMAIL_NOT_VERIFIED);
		}
		if (socialAccountRepository.findByMemberIdAndProvider(member.getId(), profile.provider()).isPresent()) {
			// 이 회원에는 다른 카카오(구글) 계정이 이미 연결되어 있다.
			throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_CONFLICT);
		}
		socialAccountRepository.saveAndFlush(MemberSocialAccount.link(member.getId(), profile.provider(),
				profile.providerUserId(), email));
	}

	private AuthTokens tokensFor(Member member, String email) {
		rejectAdmin(member, email);
		if (member.getStatus() == MemberStatus.SUSPENDED) {
			throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
		}
		return authService.issueMemberTokens(member);
	}

	/**
	 * 관리자는 관리자 로그인으로만 들어온다(#163). 트랜잭션을 되돌리고(연결·토큰을 남기지 않고) 트랜잭션 밖에서 감사 로그에 남긴 뒤,
	 * 계정이 있는지 드러내지 않게 실패로 응답한다(inTransaction).
	 */
	private void rejectAdmin(Member member, String email) {
		if (member.getRole().canAccessAdminApi()) {
			throw new AdminAccountRejected(email);
		}
	}

	private SocialIdentityClient client(SocialProvider provider) {
		return switch (provider) {
			case GOOGLE -> googleClient;
			case KAKAO -> kakaoClient;
		};
	}

	private <T> T inTransaction(Supplier<T> work) {
		return transactionTemplate.execute(status -> work.get());
	}

	/** 관리자 계정을 만나면 되돌린 트랜잭션 밖에서 감사 로그를 남기고 401(SOCIAL_LOGIN_FAILED) */
	private <T> T inTransaction(Supplier<T> work, String clientIp) {
		try {
			return inTransaction(work);
		} catch (AdminAccountRejected e) {
			try {
				auditLogService.recordAdminLogin(null, clientIp, e.email, 401, ADMIN_SOCIAL_LOGIN_REJECTED);
			} catch (RuntimeException auditFailure) {
				log.error("관리자 소셜 로그인 감사 로그 저장 실패: ip={}", clientIp, auditFailure);
			}
			throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
		}
	}

	/** 관리자 계정을 만났다는 표시. 트랜잭션을 되돌리려고 던진다. */
	private static final class AdminAccountRejected extends RuntimeException {

		private final transient String email;

		AdminAccountRejected(String email) {
			super(null, null, false, false);
			this.email = email;
		}
	}
}
