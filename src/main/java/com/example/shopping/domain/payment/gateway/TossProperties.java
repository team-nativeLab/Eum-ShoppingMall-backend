package com.example.shopping.domain.payment.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param secretKey 토스페이먼츠 시크릿 키. 환경변수로만 주입하고 코드·설정 파일에 적지 않는다.
 */
@ConfigurationProperties("payment.toss")
public record TossProperties(String secretKey, @DefaultValue("https://api.tosspayments.com") String baseUrl) {
}
