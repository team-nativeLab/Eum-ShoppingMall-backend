package com.example.shopping.domain.product.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.util.StringUtils;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

	/** 관리자 상품 목록에서 ?sort=로 지정할 수 있는 정렬 기준 */
	public static final Set<String> SORTABLE = Set.of("id", "name", "basePrice", "createdAt", "recommendScore",
			"salesCount");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id")
	private Category category;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(length = 100)
	private String brand;

	@Column(columnDefinition = "TEXT")
	private String description;

	// 대표 이미지. 파일 업로드 API가 돌려준 URL을 넣는다.
	@Column(length = 500)
	private String imageUrl;

	// 금액은 원 단위 정수
	@Column(nullable = false)
	private long basePrice;

	// 정가. 판매가(basePrice)보다 높으면 화면에 할인 전 가격으로 줄을 그어 보여 준다. 없으면 null
	private Long listPrice;

	// false면 "금액 미표시" 상품 (WEB-006)
	@Column(nullable = false)
	private boolean priceVisible = true;

	// 받은 안전 인증들. 인증 종류 필터와 "안전 인증 제품만 보기"에 쓴다.
	@ElementCollection
	@CollectionTable(name = "product_certification", joinColumns = @JoinColumn(name = "product_id"),
			foreignKey = @ForeignKey(name = "fk_product_certification_product"))
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "certification", nullable = false, length = 30)
	private Set<Certification> certifications = new HashSet<>();

	// 추천순 정렬에 쓰는 점수. 관리자가 정하며 클수록 앞에 나온다.
	@ColumnDefault("0")
	@Column(nullable = false)
	private int recommendScore;

	// 결제 완료된 누적 판매 수량. 인기순 정렬에 쓴다. 주문이 취소되면 그만큼 줄어든다.
	@ColumnDefault("0")
	@Column(nullable = false)
	private long salesCount;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private ProductStatus status = ProductStatus.ON_SALE;

	@OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
	private List<ProductSku> skus = new ArrayList<>();

	/** @param listPrice 정가. 없으면 null 또는 0 */
	public static Product create(Category category, String name, String description, String imageUrl, long basePrice,
			Long listPrice, boolean priceVisible, ProductStatus status) {
		Product product = new Product();
		product.category = category;
		product.name = name;
		product.description = blankToNull(description);
		product.imageUrl = blankToNull(imageUrl);
		product.basePrice = basePrice;
		product.listPrice = zeroToNull(listPrice);
		product.priceVisible = priceVisible;
		product.status = status;
		return product;
	}

	/** null인 항목은 바꾸지 않는다. 설명·이미지는 빈 문자열을, 정가는 0을 주면 지운다. */
	public void update(Category category, String name, String description, String imageUrl, Long basePrice,
			Long listPrice, ProductStatus status) {
		if (category != null) {
			this.category = category;
		}
		if (name != null) {
			this.name = name;
		}
		if (description != null) {
			this.description = blankToNull(description);
		}
		if (imageUrl != null) {
			this.imageUrl = blankToNull(imageUrl);
		}
		if (basePrice != null) {
			this.basePrice = basePrice;
		}
		if (listPrice != null) {
			this.listPrice = zeroToNull(listPrice);
		}
		if (status != null) {
			this.status = status;
		}
	}

	/**
	 * 검색 필터·정렬에 쓰는 값. null인 항목은 바꾸지 않는다.
	 * 브랜드는 빈 문자열을, 인증은 빈 목록을 주면 지운다. 인증은 보낸 목록으로 통째로 바꾼다.
	 */
	public void updateSearchAttributes(String brand, Collection<Certification> certifications,
			Integer recommendScore) {
		if (brand != null) {
			this.brand = blankToNull(brand);
		}
		if (certifications != null) {
			this.certifications.clear();
			this.certifications.addAll(certifications);
		}
		if (recommendScore != null) {
			this.recommendScore = recommendScore;
		}
	}

	public void addSku(String optionName, long price, int stock) {
		skus.add(ProductSku.create(this, optionName, price, stock));
	}

	public List<ProductSku> getActiveSkus() {
		return skus.stream().filter(ProductSku::isActive).toList();
	}

	public void changePriceVisibility(boolean priceVisible) {
		this.priceVisible = priceVisible;
	}

	/** 소프트 삭제. 주문 내역이 참조하므로 행은 지우지 않는다. */
	public void delete() {
		this.status = ProductStatus.DELETED;
	}

	/** 금액 미표시 상품은 가격을 응답에 싣지 않도록 null을 돌려준다. */
	public Long getDisplayPrice() {
		return priceVisible ? basePrice : null;
	}

	/** 정가도 판매가와 함께 숨긴다. */
	public Long getDisplayListPrice() {
		return priceVisible ? listPrice : null;
	}

	/** 인증을 정해진 순서(KCs 안전인증, 자율안전확인, 형식승인)로 돌려준다. */
	public Set<Certification> getCertifications() {
		return certifications.isEmpty() ? EnumSet.noneOf(Certification.class) : EnumSet.copyOf(certifications);
	}

	/**
	 * 품절 여부. 관리자가 품절로 지정했거나, 옵션이 있는데 모든 옵션의 재고가 없으면 품절이다.
	 * 옵션이 하나도 없는 상품(견적 상품 등)은 재고로 판단하지 않는다.
	 */
	public boolean isSoldOut() {
		List<ProductSku> activeSkus = getActiveSkus();
		return status == ProductStatus.SOLD_OUT
				|| (!activeSkus.isEmpty() && activeSkus.stream().allMatch(sku -> sku.getStock() <= 0));
	}

	/** 옵션이 여러 개라 장바구니에 담기 전에 옵션을 골라야 하는지 */
	public boolean isOptionRequired() {
		return getActiveSkus().size() > 1;
	}

	/** 옵션이 하나뿐이면 그 옵션. 목록에서 옵션 선택 없이 바로 장바구니에 담을 때 쓴다. */
	public Long getDefaultSkuId() {
		List<ProductSku> activeSkus = getActiveSkus();
		return activeSkus.size() == 1 ? activeSkus.getFirst().getId() : null;
	}

	private static String blankToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private static Long zeroToNull(Long value) {
		return value == null || value == 0 ? null : value;
	}
}
