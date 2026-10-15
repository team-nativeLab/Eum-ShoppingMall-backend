package com.example.shopping.domain.product.dto;

import java.util.Collection;
import java.util.List;

import com.example.shopping.domain.product.entity.Certification;

/**
 * @param code  요청에 쓰는 값 (예: KCS)
 * @param label 화면에 보여 줄 이름 (예: KCs 안전인증)
 */
public record CertificationResponse(String code, String label) {

	public static CertificationResponse from(Certification certification) {
		return new CertificationResponse(certification.name(), certification.getLabel());
	}

	public static List<CertificationResponse> listOf(Collection<Certification> certifications) {
		return certifications.stream().map(CertificationResponse::from).toList();
	}
}
