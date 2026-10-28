package com.example.shopping.domain.member.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.shopping.domain.member.dto.response.MeResponse;
import com.example.shopping.domain.member.dto.request.PasswordChangeRequest;
import com.example.shopping.domain.member.dto.request.ProfileUpdateRequest;
import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.domain.cart.repository.CartItemRepository;
import com.example.shopping.domain.order.repository.OrderRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.security.MemberTokenStateCache;
import com.example.shopping.global.validation.ContactFormats;

import lombok.RequiredArgsConstructor;

/** 로그인한 회원 본인의 정보. 비밀번호 해시 계산 동안 DB 커넥션을 잡지 않도록 메서드를 트랜잭션으로 묶지 않는다. */
@Service
@RequiredArgsConstructor
public class MemberService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenService refreshTokenService;
	private final CartItemRepository cartItemRepository;
	private final OrderRepository orderRepository;
	private final MemberTokenStateCache memberTokenStateCache;

	public MeResponse getMe(Long memberId) {
		return MeResponse.from(findActive(memberId));
	}

	public MeResponse updateProfile(Long memberId, ProfileUpdateRequest request) {
		Member member = findActive(memberId);
		String name = request.name() == null ? null : request.name().trim();
		String phone = ContactFormats.phone(request.phone());
		member.updateProfile(name, phone);
		return MeResponse.from(memberRepository.save(member));
	}

	/** 비밀번호를 바꾸면 다른 기기에 남아 있는 로그인(Refresh 토큰)을 모두 끊는다. 비밀번호가 유출됐을 때를 대비한 조치다. */
	public void changePassword(Long memberId, PasswordChangeRequest request) {
		Member member = findActive(memberId);
		if (!passwordEncoder.matches(request.currentPassword(), member.getPasswordHash())) {
			throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
		}
		if (request.newPassword().equals(request.currentPassword())) {
			throw new BusinessException(ErrorCode.PASSWORD_NOT_CHANGED);
		}
		member.changePassword(passwordEncoder.encode(request.newPassword()));
		memberRepository.save(member);
		memberTokenStateCache.refresh(member);
		refreshTokenService.revokeAll(memberId);
	}

	@org.springframework.transaction.annotation.Transactional
	public void withdraw(Long memberId, String password) {
		Member member = findActive(memberId);
		if (!passwordEncoder.matches(password, member.getPasswordHash())) {
			throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
		}
		if (orderRepository.countOrdersBlockingWithdrawal(memberId) > 0) {
			throw new BusinessException(ErrorCode.ACCOUNT_HAS_ACTIVE_ORDERS);
		}
		cartItemRepository.deleteAllByMemberId(memberId);
		member.withdraw();
		memberRepository.save(member);
		memberTokenStateCache.refresh(member);
		refreshTokenService.revokeAll(memberId);
	}

	/** 토큰이 유효해도 그사이 탈퇴·정지된 계정이면 거절한다. */
	private Member findActive(Long memberId) {
		return memberRepository.findById(memberId)
				.filter(member -> member.getStatus() == MemberStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}
}
