package com.example.shopping.domain.product.entity;

import org.hibernate.annotations.ColumnDefault;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 브랜드 소개(브랜드 목록·상세 페이지). 상품과는 이름으로 이어진다: 상품의 brand 값이 이 name과 같으면 이 브랜드의 상품이다.
 * 관리자가 입력한 글은 화면에서 텍스트로만 그린다. 이미지는 업로드 API가 돌려준 주소만 받는다.
 */
@Entity
@Table(name = "brand")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brand extends BaseTimeEntity {

	/** 대표 색상을 정하지 않은 브랜드(이전에 등록한 브랜드 포함)의 색 */
	public static final String DEFAULT_ACCENT_COLOR = "#2F6F62";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 상품의 brand 값과 같은 이름
	@Column(nullable = false, unique = true, length = 100)
	private String name;

	@Column(length = 100)
	private String englishName;

	@Column(length = 500)
	private String logoUrl;

	// 한 줄 소개
	@Column(length = 200)
	private String tagline;

	@Column(length = 500)
	private String coverImageUrl;

	// 주력 분야
	@Column(length = 100)
	private String mainField;

	@Column(length = 50)
	private String originCountry;

	// 주요 인증 표시 문구
	@Column(length = 200)
	private String certification;

	@Column(length = 100)
	private String storyTitle;

	@Column(columnDefinition = "TEXT")
	private String storyBody;

	@Column(nullable = false)
	private boolean visible;

	// 브랜드 목록 위의 대표 브랜드
	@Column(nullable = false)
	private boolean featured;

	@Column(nullable = false)
	private int sortOrder;

	// 대표 색상(#RRGGBB). 웹 브랜드 선택 패널과 버튼에 쓴다(#214).
	@ColumnDefault("'" + DEFAULT_ACCENT_COLOR + "'")
	@Column(nullable = false, length = 7)
	private String accentColor = DEFAULT_ACCENT_COLOR;

	// 로고 표시 방식(BrandImageSettings)
	@ColumnDefault("'contain'")
	@Column(nullable = false, length = 10)
	private String logoFit = BrandImageSettings.DEFAULT_LOGO.fit();

	@ColumnDefault("240")
	@Column(nullable = false)
	private int logoWidth = BrandImageSettings.DEFAULT_LOGO.width();

	@ColumnDefault("96")
	@Column(nullable = false)
	private int logoHeight = BrandImageSettings.DEFAULT_LOGO.height();

	// 커버 표시 방식(BrandImageSettings)
	@ColumnDefault("'cover'")
	@Column(nullable = false, length = 10)
	private String coverFit = BrandImageSettings.DEFAULT_COVER.fit();

	@ColumnDefault("1200")
	@Column(nullable = false)
	private int coverWidth = BrandImageSettings.DEFAULT_COVER.width();

	@ColumnDefault("704")
	@Column(nullable = false)
	private int coverHeight = BrandImageSettings.DEFAULT_COVER.height();

	public static Brand create(Fields fields) {
		Brand brand = new Brand();
		brand.change(fields);
		return brand;
	}

	public void change(Fields fields) {
		this.name = fields.name();
		this.englishName = fields.englishName();
		this.logoUrl = fields.logoUrl();
		this.tagline = fields.tagline();
		this.coverImageUrl = fields.coverImageUrl();
		this.mainField = fields.mainField();
		this.originCountry = fields.originCountry();
		this.certification = fields.certification();
		this.storyTitle = fields.storyTitle();
		this.storyBody = fields.storyBody();
		this.visible = fields.visible();
		this.featured = fields.featured();
		this.sortOrder = fields.sortOrder();
		this.accentColor = fields.accentColor();
		this.logoFit = fields.logoSettings().fit();
		this.logoWidth = fields.logoSettings().width();
		this.logoHeight = fields.logoSettings().height();
		this.coverFit = fields.coverSettings().fit();
		this.coverWidth = fields.coverSettings().width();
		this.coverHeight = fields.coverSettings().height();
	}

	public BrandImageSettings getLogoSettings() {
		return new BrandImageSettings(logoFit, logoWidth, logoHeight);
	}

	public BrandImageSettings getCoverSettings() {
		return new BrandImageSettings(coverFit, coverWidth, coverHeight);
	}

	/** 저장할 값. 빈 문자열은 서비스에서 null로 바꿔 넘긴다. 대표 색상과 이미지 표시 방식은 보내지 않으면 기본값으로 채워 넘긴다. */
	public record Fields(String name, String englishName, String logoUrl, String tagline, String coverImageUrl,
			String mainField, String originCountry, String certification, String storyTitle, String storyBody,
			boolean visible, boolean featured, int sortOrder, String accentColor, BrandImageSettings logoSettings,
			BrandImageSettings coverSettings) {
	}
}
