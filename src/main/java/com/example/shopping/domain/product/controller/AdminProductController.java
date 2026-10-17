package com.example.shopping.domain.product.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.product.dto.AdminProductDetailResponse;
import com.example.shopping.domain.product.dto.AdminProductSummaryResponse;
import com.example.shopping.domain.product.dto.PriceVisibilityRequest;
import com.example.shopping.domain.product.dto.ProductCreateRequest;
import com.example.shopping.domain.product.dto.ProductUpdateRequest;
import com.example.shopping.domain.product.dto.StockReceiveRequest;
import com.example.shopping.domain.product.dto.SkuCreateRequest;
import com.example.shopping.domain.product.dto.SkuUpdateRequest;
import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.domain.product.service.AdminProductService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.response.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

	private final AdminProductService adminProductService;

	/** ADM-003, 004 전체 상품 목록·검색 (?status=&categoryId=&keyword=, 비공개 포함) */
	@GetMapping
	public ApiResponse<PageResponse<AdminProductSummaryResponse>> products(
			@RequestParam(required = false) ProductStatus status,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) String keyword,
			@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
		return ApiResponse.ok(adminProductService.search(status, categoryId, keyword, pageable));
	}

	/** 수정 화면용 상품 상세. 고객용 상세와 달리 비공개 상품과 숨긴 가격·재고 수량까지 내려준다. */
	@GetMapping("/{productId}")
	public ApiResponse<AdminProductDetailResponse> product(@PathVariable Long productId) {
		return ApiResponse.ok(adminProductService.getDetail(productId));
	}

	/** ADM-006 상품과 옵션(SKU) 등록 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<AdminProductDetailResponse> createProduct(@Valid @RequestBody ProductCreateRequest request) {
		return ApiResponse.ok(adminProductService.create(request));
	}

	/** ADM-004 상품 정보·판매 상태 변경. 옵션은 SKU 전용 API에서 관리한다. */
	@PatchMapping("/{productId}")
	public ApiResponse<AdminProductDetailResponse> updateProduct(@PathVariable Long productId,
			@Valid @RequestBody ProductUpdateRequest request) {
		return ApiResponse.ok(adminProductService.update(productId, request));
	}

	/** 상품 옵션 추가 */
	@PostMapping("/{productId}/skus")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<AdminProductDetailResponse> addSku(@PathVariable Long productId,
			@Valid @RequestBody SkuCreateRequest request) {
		return ApiResponse.ok(adminProductService.addSku(productId, request));
	}

	/** 상품 옵션명·가격 수정. 재고는 이 API로 바꾸지 않는다. */
	@PatchMapping("/{productId}/skus/{skuId}")
	public ApiResponse<AdminProductDetailResponse> updateSku(@PathVariable Long productId, @PathVariable Long skuId,
			@Valid @RequestBody SkuUpdateRequest request) {
		return ApiResponse.ok(adminProductService.updateSku(productId, skuId, request));
	}

	/** 주문 이력을 보존하기 위해 SKU 행을 지우지 않고 판매 중지한다. */
	@DeleteMapping("/{productId}/skus/{skuId}")
	public ApiResponse<AdminProductDetailResponse> deactivateSku(@PathVariable Long productId,
			@PathVariable Long skuId) {
		return ApiResponse.ok(adminProductService.deactivateSku(productId, skuId));
	}

	/** ADM-003, 004 상품 삭제. 행을 지우지 않고 상태만 DELETED로 바꾼다. */
	@DeleteMapping("/{productId}")
	public ApiResponse<Void> deleteProduct(@PathVariable Long productId) {
		adminProductService.delete(productId);
		return ApiResponse.ok(null);
	}

	/** 옵션(SKU) 입고. 현재 재고에 입고 수량을 더한다. 재고를 줄이거나 특정 수량으로 맞출 때는 상품 수정을 쓴다. */
	@PostMapping("/{productId}/skus/{skuId}/stock-in")
	public ApiResponse<AdminProductDetailResponse> receiveStock(@PathVariable Long productId,
			@PathVariable Long skuId, @Valid @RequestBody StockReceiveRequest request) {
		return ApiResponse.ok(adminProductService.receiveStock(productId, skuId, request.quantity()));
	}

	/** ADM-005 금액 표시 여부 전환 */
	@PatchMapping("/{productId}/visibility")
	public ApiResponse<AdminProductDetailResponse> changePriceVisibility(@PathVariable Long productId,
			@Valid @RequestBody PriceVisibilityRequest request) {
		return ApiResponse.ok(adminProductService.changePriceVisibility(productId, request.priceVisible()));
	}
}
