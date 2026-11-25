package com.example.shopping.domain.product.service;

/**
 * 상품의 베스트 지정이 바뀌거나 상품을 지울 때 받는 쪽(#219). 홈 편성의 베스트 목록(HomeFeaturedProducts)이 구현해
 * 상품의 베스트 지정과 홈 베스트 목록을 같은 목록으로 유지한다. 관리자 상품 변경과 같은 트랜잭션에서 부른다.
 */
public interface FeaturedProductListener {

	/** 베스트로 지정하면 홈 베스트 목록 끝에 넣고, 지정을 풀면 뺀다. */
	void featuredChanged(Long productId, boolean featured);

	/** 지운 상품을 홈 베스트 목록에서 뺀다. */
	void productDeleted(Long productId);
}
