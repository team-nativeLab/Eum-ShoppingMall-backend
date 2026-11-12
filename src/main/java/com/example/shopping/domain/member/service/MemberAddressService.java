package com.example.shopping.domain.member.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.member.dto.request.MemberAddressRequest;
import com.example.shopping.domain.member.dto.response.MemberAddressResponse;
import com.example.shopping.domain.member.entity.MemberAddress;
import com.example.shopping.domain.member.repository.MemberAddressRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.validation.ContactFormats;

import lombok.RequiredArgsConstructor;

/**
 * 회원 배송지(주소록). 회원마다 10개까지 저장하고, 그중 하나가 기본 배송지다.
 * 남의 배송지는 있는지도 알 수 없게 없는 배송지와 똑같이 404로 답한다.
 */
@Service
@RequiredArgsConstructor
public class MemberAddressService {

	private final MemberAddressRepository repository;

	/** 기본 배송지를 먼저, 나머지는 최근에 고친 순서로 */
	@Transactional(readOnly = true)
	public List<MemberAddressResponse> list(Long memberId) {
		return repository.findAllOfMember(memberId).stream().map(MemberAddressResponse::from).toList();
	}

	/** 처음 등록하는 배송지이거나 isDefault가 true면 기본 배송지가 된다. 10개를 넘으면 400 */
	@Transactional
	public MemberAddressResponse create(Long memberId, MemberAddressRequest request) {
		long count = repository.countByMemberId(memberId);
		if (count >= MemberAddress.MAX_PER_MEMBER) {
			throw new BusinessException(ErrorCode.ADDRESS_LIMIT_EXCEEDED);
		}
		boolean makeDefault = count == 0 || Boolean.TRUE.equals(request.isDefault());
		MemberAddress saved = repository.save(MemberAddress.create(memberId, fields(request), makeDefault));
		if (makeDefault) {
			repository.clearDefaultExcept(memberId, saved.getId());
		}
		return MemberAddressResponse.from(saved);
	}

	/**
	 * 내용을 바꾼다. isDefault가 true면 기본 배송지로 정한다.
	 * 기본 배송지는 다른 배송지를 기본으로 정해야만 바뀐다(false를 보내도 기본 배송지는 그대로).
	 */
	@Transactional
	public MemberAddressResponse update(Long memberId, Long addressId, MemberAddressRequest request) {
		MemberAddress address = find(memberId, addressId);
		address.change(fields(request));
		if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefaultAddress()) {
			address.makeDefault();
			repository.clearDefaultExcept(memberId, addressId);
		}
		return MemberAddressResponse.from(address);
	}

	/** 기본 배송지를 지우면 남은 배송지 중 최근에 고친 것이 기본 배송지가 된다. */
	@Transactional
	public void delete(Long memberId, Long addressId) {
		MemberAddress address = find(memberId, addressId);
		repository.delete(address);
		repository.flush();
		if (address.isDefaultAddress()) {
			repository.findAllOfMember(memberId).stream().findFirst().ifPresent(MemberAddress::makeDefault);
		}
	}

	private MemberAddress find(Long memberId, Long addressId) {
		return repository.findByIdAndMemberId(addressId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
	}

	private static MemberAddress.Fields fields(MemberAddressRequest request) {
		return new MemberAddress.Fields(request.label().trim(), request.recipient().trim(),
				ContactFormats.phone(request.recipientPhone()), request.zipCode(), request.address().trim(),
				StringUtils.hasText(request.addressDetail()) ? request.addressDetail().trim() : null);
	}
}
