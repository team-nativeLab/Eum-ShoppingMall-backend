package com.example.shopping.global.captcha;

/** 로봇 확인(CAPTCHA) 토큰을 확인 서비스에 물어본다. */
public interface CaptchaVerifier {

	/** 로봇 확인을 쓰는지. 끄면 토큰을 요구하지 않는다. */
	boolean isEnabled();

	/**
	 * 사용자가 위젯을 풀고 받은 토큰을 확인한다. 토큰은 한 번만 쓸 수 있으므로 요청마다 새로 받아야 한다.
	 * 허용한 사이트(app.captcha.allowed-hostnames)에서 이 용도로 푼 토큰만 통과시킨다.
	 *
	 * @param action   프론트가 위젯에 넣은 용도 이름(Turnstile의 action). 다른 화면의 위젯에서 받은 토큰을 막는다.
	 * @param remoteIp 서버가 확인한 클라이언트 IP. 확인 서비스가 토큰을 받은 IP와 비교한다.
	 * @return 사람이 이 사이트에서 이 용도로 푼 토큰이면 true
	 * @throws com.example.shopping.global.exception.BusinessException 확인 서비스에 닿지 않으면 CAPTCHA_UNAVAILABLE(503)
	 */
	boolean verify(String token, String action, String remoteIp);
}
