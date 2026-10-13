package com.example.shopping.domain.cart.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.cart.dto.CartItemAddRequest;
import com.example.shopping.domain.cart.dto.CartItemUpdateRequest;
import com.example.shopping.domain.cart.dto.CartResponse;
import com.example.shopping.domain.cart.entity.CartItem;
import com.example.shopping.domain.cart.repository.CartItemRepository;
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

	@Transactional(readOnly = true)
	public CartResponse getCart(Long memberId) {
		return CartResponse.from(cartItemRepository.findAllWithProduct(memberId));
	}

	/** 이미 담긴 옵션이면 수량을 더한다. 금액 미표시 상품도 담을 수는 있다(주문은 불가). */
	public CartResponse addItem(Long memberId, CartItemAddRequest request) {
		ProductSku sku = productSkuRepository.findWithProductById(request.skuId())
				.filter(s -> ProductStatus.CUSTOMER_VISIBLE.contains(s.getProduct().getStatus()))
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

		CartItem existing = cartItemRepository.findByMemberIdAndSkuId(memberId, sku.getId()).orElse(null);
		if (existing != null) {
			existing.changeQuantity(existing.getQuantity() + request.quantity());
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
