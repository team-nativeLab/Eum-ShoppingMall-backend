package com.example.shopping.domain.product.service;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.global.privacy.Masking;

/** 상품 문의·리뷰처럼 누구나 보는 글의 작성자 표시. 이름을 가리고, 탈퇴한 회원은 "탈퇴한 회원"으로 보인다. */
final class AuthorNames {

	static final String WITHDRAWN = "탈퇴한 회원";

	private AuthorNames() {
	}

	static String masked(Member author) {
		if (author == null || author.getStatus() == MemberStatus.WITHDRAWN) {
			return WITHDRAWN;
		}
		return Masking.name(author.getName());
	}

	/** 한 페이지의 작성자를 한 번에 읽는다. */
	static Map<Long, Member> load(MemberRepository memberRepository, Collection<Long> memberIds) {
		return memberRepository.findAllById(memberIds).stream()
				.collect(Collectors.toMap(Member::getId, Function.identity()));
	}
}
