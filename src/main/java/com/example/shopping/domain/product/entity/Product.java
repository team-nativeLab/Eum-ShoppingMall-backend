package com.example.shopping.domain.product.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.util.StringUtils;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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

	// false면 "금액 미표시" 상품 (WEB-006)
	@Column(nullable = false)
	private boolean priceVisible = true;

	// 안전 인증을 받은 제품인지. "안전 인증 제품만 보기" 필터에 쓴다.
	@ColumnDefault("false")
	@Column(nullable = false)
	private boolean safetyCertified;

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

	public static Product create(Category category, String name, String description, String imageUrl, long basePrice,
			boolean priceVisible, ProductStatus status) {
		Product product = new Product();
		product.category = category;
		product.name = name;
		product.description = blankToNull(description);
		product.imageUrl = blankToNull(imageUrl);
		product.basePrice = basePrice;
		product.priceVisible = priceVisible;
		product.status = status;
		return product;
	}

	/** null인 항목은 바꾸지 않는다. 설명·이미지는 빈 문자열을 주면 지운다. */
	public void update(Category category, String name, String description, String imageUrl, Long basePrice,
			ProductStatus status) {
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
		if (status != null) {
			this.status = status;
		}
	}

	/** 검색 필터·정렬에 쓰는 값. null인 항목은 바꾸지 않는다. 브랜드는 빈 문자열을 주면 지운다. */
	public void updateSearchAttributes(String brand, Boolean safetyCertified, Integer recommendScore) {
		if (brand != null) {
			this.brand = blankToNull(brand);
		}
		if (safetyCertified != null) {
			this.safetyCertified = safetyCertified;
		}
		if (recommendScore != null) {
			this.recommendScore = recommendScore;
		}
	}

	public void addSku(String optionName, long price, int stock) {
		skus.add(ProductSku.create(this, optionName, price, stock));
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

	private static String blankToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
}
