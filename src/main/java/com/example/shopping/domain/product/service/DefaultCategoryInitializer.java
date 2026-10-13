package com.example.shopping.domain.product.service;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.product.entity.Category;
import com.example.shopping.domain.product.repository.CategoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 카테고리가 하나도 없을 때(처음 띄울 때) 기본 카테고리를 만들어 둔다.
 * 이후에는 관리자가 카테고리 관리 API로 이름·순서를 바꾸거나 추가·삭제하며, 이미 카테고리가 있으면 아무것도 하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.default-categories", havingValue = "true", matchIfMissing = true)
public class DefaultCategoryInitializer implements ApplicationRunner {

	public static final List<String> DEFAULT_CATEGORIES = List.of(
			"안전모", "보안경", "안전장갑", "방진마스크", "안전화", "귀마개");

	private final CategoryRepository categoryRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (categoryRepository.count() > 0) {
			return;
		}
		for (int i = 0; i < DEFAULT_CATEGORIES.size(); i++) {
			categoryRepository.save(Category.create(null, DEFAULT_CATEGORIES.get(i), i + 1, true));
		}
		log.info("기본 카테고리 {}개를 만들었습니다: {}", DEFAULT_CATEGORIES.size(), DEFAULT_CATEGORIES);
	}
}
