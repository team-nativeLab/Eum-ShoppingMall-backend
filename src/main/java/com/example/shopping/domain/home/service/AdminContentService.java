package com.example.shopping.domain.home.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.home.dto.response.ContentResponse;
import com.example.shopping.domain.home.dto.request.SiteContentUpsertRequest;
import com.example.shopping.domain.home.entity.SiteContent;
import com.example.shopping.domain.home.repository.SiteContentRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminContentService {

	private static final Pattern HTML_TAG = Pattern.compile("<\\s*/?\\s*[a-zA-Z][^>]*>");

	private final SiteContentRepository siteContentRepository;

	public List<ContentResponse> list() {
		return siteContentRepository.findAllByOrderByCodeAsc().stream().map(ContentResponse::from).toList();
	}

	public ContentResponse get(String code) {
		validateCode(code);
		return siteContentRepository.findByCode(code).map(ContentResponse::from)
				.orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
	}

	@Transactional
	public ContentResponse upsert(String code, SiteContentUpsertRequest request) {
		validateCode(code);
		String title = request.title().trim();
		String body = request.body();
		if (HTML_TAG.matcher(body).find()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		SiteContent content = siteContentRepository.findByCode(code)
				.orElseGet(() -> SiteContent.create(code, title, body));
		content.update(title, body);
		return ContentResponse.from(siteContentRepository.saveAndFlush(content));
	}

	private void validateCode(String code) {
		if (!SiteContent.ALLOWED_CODES.contains(code)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}
}
