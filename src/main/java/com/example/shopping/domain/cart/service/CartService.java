package com.example.shopping.domain.cart.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.cart.dto.request.CartItemAddRequest;
import com.example.shopping.domain.cart.dto.request.CartItemUpdateRequest;
import com.example.shopping.domain.cart.dto.response.CartResponse;
import com.example.shopping.domain.cart.entity.CartItem;
import com.example.shopping.domain.cart.repository.CartItemRepository;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.domain.product.entity.ProductSku;
import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.domain.product.repository.ProductSkuRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 모든 조회·변경은 로그인한 회원 본인의 장바구니로만 한정한다. */
@Service
@RequiredArgsConstructor
@Transactional
public class CartService {

	private final CartItemRepository cartItemRepository;
	private final ProductSkuRepository productSkuRepository;
	private final MemberRepository memberRepository;

	@Transactional(readOnly = true)
	public CartResponse getCart(Long memberId) {
		return CartResponse.from(cartItemRepository.findAllWithProduct(memberId));
	}

	/** 이미 담긴 옵션이면 수량을 더한다. 금액 미표시 상품도 담을 수는 있다(주문은 불가). */
	public CartResponse addItem(Long memberId, CartItemAddRequest request) {
		// 같은 회원의 동시 요청만 한 줄로 세운다. 그래야 같은 옵션의 첫 행이 두 번 들어가거나 수량이 빠지지 않고,
		// 담을 수 있는 종류 수(MAX_KINDS)도 넘지 않는다. 모든 회원이 함께 쓰는 옵션 행은 잠그지 않으므로
		// 다른 회원의 장바구니 담기나 주문(재고 차감)·입고와 서로 기다리지 않는다. 회원이 있는지는 토큰 검증에서 이미 확인했다.
		memberRepository.findByIdForUpdate(memberId);
		ProductSku sku = productSkuRepository.findWithProductById(request.skuId())
				.filter(ProductSku::isActive)
				.filter(s -> ProductStatus.CUSTOMER_VISIBLE.contains(s.getProduct().getStatus()))
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

		CartItem existing = cartItemRepository.findByMemberIdAndSkuId(memberId, sku.getId()).orElse(null);
		if (existing != null) {
			existing.addQuantity(request.quantity());
		} else {
			if (cartItemRepository.countByMemberId(memberId) >= CartItem.MAX_KINDS) {
				throw new BusinessException(ErrorCode.CART_LIMIT_EXCEEDED);
			}
			cartItemRepository.save(CartItem.create(memberId, sku, request.quantity()));
		}
		return getCart(memberId);
	}

	public CartResponse updateItem(Long memberId, Long skuId, CartItemUpdateRequest request) {
		CartItem item = find(memberId, skuId);
		if (request.quantity() != null) {
			item.changeQuantity(request.quantity());
		}
		if (request.selected() != null) {
			item.changeSelected(request.selected());
		}
		return getCart(memberId);
	}

	public CartResponse removeItem(Long memberId, Long skuId) {
		cartItemRepository.delete(find(memberId, skuId));
		return getCart(memberId);
	}

	private CartItem find(Long memberId, Long skuId) {
		return cartItemRepository.findByMemberIdAndSkuId(memberId, skuId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
	}
}
