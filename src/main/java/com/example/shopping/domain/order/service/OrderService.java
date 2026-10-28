package com.example.shopping.domain.order.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.cart.entity.CartItemStatus;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.domain.order.dto.request.OrderCreateRequest;
import com.example.shopping.domain.order.dto.response.OrderCreateResponse;
import com.example.shopping.domain.order.dto.response.OrderDetailResponse;
import com.example.shopping.domain.order.dto.response.OrderSummaryResponse;
import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderItem;
import com.example.shopping.domain.order.entity.ShippingAddress;
import com.example.shopping.domain.order.repository.OrderRepository;
import com.example.shopping.domain.payment.repository.PaymentRepository;
import com.example.shopping.domain.product.entity.ProductSku;
import com.example.shopping.domain.product.repository.ProductSkuRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.validation.SortValidator;

import lombok.RequiredArgsConstructor;

/** 고객의 주문. 모든 조회는 로그인한 회원 본인의 주문으로만 한정한다. */
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

	// 주문 후 이 시간 안에 결제하지 않으면 만료되고 잡아 둔 재고가 풀린다.
	private static final int PAYMENT_DEADLINE_MINUTES = 15;
	private static final String ORDER_NO_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int ORDER_NO_RANDOM_LENGTH = 16;

	private final OrderRepository orderRepository;
	private final ProductSkuRepository productSkuRepository;
	private final PaymentRepository paymentRepository;
	private final MemberRepository memberRepository;
	private final OrderTxService orderTxService;
	private final ShippingPolicyService shippingPolicyService;
	private final SecureRandom secureRandom = new SecureRandom();

	/**
	 * 요청에 담긴 품목과 배송지로 결제 대기 주문을 만든다. 서버에 저장해 둔 상태(장바구니 선택, 배송지)에 기대지 않으므로
	 * 다른 탭·기기에서 장바구니를 바꿔도 화면에 보인 내용과 다른 주문이 만들어지지 않는다.
	 * 금액은 요청으로 받지 않고 DB의 현재 가격으로 서버가 계산한다 (클라이언트가 보낸 금액은 조작될 수 있다).
	 *
	 * @param idempotencyKey 같은 키로 다시 요청하면 새로 만들지 않고 처음 만든 주문을 돌려준다. 없으면 null
	 */
	public OrderCreateResponse createOrder(Long memberId, String idempotencyKey, OrderCreateRequest request) {
		if (idempotencyKey != null) {
			Optional<Order> existing = orderRepository.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey);
			if (existing.isPresent()) {
				return OrderCreateResponse.from(existing.get());
			}
		}
		// 토큰이 아직 유효하더라도 그사이 정지·탈퇴된 회원은 주문할 수 없다.
		memberRepository.findById(memberId)
				.filter(member -> member.getStatus() == MemberStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_SUSPENDED));
		ShippingAddress address = request.delivery().toAddress();
		List<OrderCreateRequest.Item> lines = sortedLines(request.items());

		// 결제하지 않고 남겨 둔 이전 주문을 정리해 그 주문이 잡고 있던 재고를 먼저 푼다.
		orderTxService.expirePendingOrdersOf(memberId);

		Order order = Order.create(memberId, newOrderNo(), address,
				LocalDateTime.now().plusMinutes(PAYMENT_DEADLINE_MINUTES), idempotencyKey);
		for (OrderCreateRequest.Item line : lines) {
			ProductSku sku = findOrderable(line.skuId(), line.quantity());
			order.addItem(sku.getId(), sku.getProduct().getName(), sku.getOptionName(), sku.getPrice(),
					line.quantity());
		}
		// 상품 합계에 지금의 배송비 정책을 적용하고 주문 시점의 금액을 저장한다.
		order.finalizeAmounts(shippingPolicyService.current());
		if (order.getItemAmount() <= 0 || order.getTotalAmount() <= 0) {
			// 결제할 수 없는 주문이 재고만 잡아 두지 않도록 만들지 않는다.
			throw new BusinessException(ErrorCode.ORDER_AMOUNT_INVALID);
		}

		// 재고를 먼저 잡아 두고(예약) 결제를 받는다. 결제하지 않으면 만료 작업이 되돌린다.
		for (OrderItem item : order.getItems()) {
			if (productSkuRepository.decreaseStock(item.getSkuId(), item.getQuantity()) == 0) {
				throw new BusinessException(ErrorCode.OUT_OF_STOCK);
			}
		}
		return OrderCreateResponse.from(orderRepository.save(order));
	}

	@Transactional(readOnly = true)
	public PageResponse<OrderSummaryResponse> getMyOrders(Long memberId, Pageable pageable) {
		SortValidator.requireAllowed(pageable, Set.of("id", "createdAt"));
		return PageResponse.from(orderRepository.findByMemberId(memberId, pageable).map(OrderSummaryResponse::from));
	}

	@Transactional(readOnly = true)
	public OrderDetailResponse getMyOrder(Long memberId, String orderNo) {
		Order order = findOwned(memberId, orderNo);
		return OrderDetailResponse.of(order,
				paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getId()).orElse(null));
	}

	/** 남의 주문번호를 넣어도 주문이 있는지조차 알 수 없도록, 없는 주문과 똑같이 404로 응답한다. */
	@Transactional(readOnly = true)
	public Order findOwned(Long memberId, String orderNo) {
		return orderRepository.findByOrderNo(orderNo)
				.filter(order -> order.getMemberId().equals(memberId))
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
	}

	/**
	 * 같은 옵션이 두 번 들어 있으면 거절한다. 여러 주문이 같은 옵션들을 서로 다른 순서로 잡다가 교착 상태에 빠지지 않도록
	 * 재고를 차감하는 순서(옵션 ID 순)로 정렬한다.
	 */
	private List<OrderCreateRequest.Item> sortedLines(List<OrderCreateRequest.Item> items) {
		if (items.stream().map(OrderCreateRequest.Item::skuId).distinct().count() != items.size()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return items.stream().sorted(Comparator.comparing(OrderCreateRequest.Item::skuId)).toList();
	}

	/** 장바구니와 같은 기준(CartItemStatus)으로 지금 이 수량을 주문할 수 있는지 확인한다. */
	private ProductSku findOrderable(Long skuId, int quantity) {
		ProductSku sku = productSkuRepository.findWithProductById(skuId)
				.orElseThrow(() -> new BusinessException(ErrorCode.SKU_NOT_FOUND));
		return switch (CartItemStatus.of(sku, quantity)) {
			case ORDERABLE -> sku;
			case PRICE_HIDDEN -> throw new BusinessException(ErrorCode.PRICE_HIDDEN_PRODUCT);
			case NOT_ON_SALE -> throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
			case OUT_OF_STOCK -> throw new BusinessException(ErrorCode.OUT_OF_STOCK);
		};
	}

	/** 날짜 + 추측할 수 없는 난수 16자. 헷갈리기 쉬운 글자(0·O, 1·I)는 쓰지 않는다. */
	private String newOrderNo() {
		StringBuilder orderNo = new StringBuilder(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
		for (int i = 0; i < ORDER_NO_RANDOM_LENGTH; i++) {
			orderNo.append(ORDER_NO_CHARS.charAt(secureRandom.nextInt(ORDER_NO_CHARS.length())));
		}
		return orderNo.toString();
	}
}
