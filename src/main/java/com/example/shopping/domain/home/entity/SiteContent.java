package com.example.shopping.domain.home.entity;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회사소개처럼 화면 하나를 채우는 고정 콘텐츠. code로 구분한다 (예: company). */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteContent extends BaseTimeEntity {

	public static final String COMPANY = "company";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String code;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String body;
}
