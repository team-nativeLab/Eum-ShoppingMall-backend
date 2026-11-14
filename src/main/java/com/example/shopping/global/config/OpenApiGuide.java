package com.example.shopping.global.config;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.example.shopping.global.exception.ErrorCode;

/**
 * API 문서 첫 화면의 안내문. 웹·앱·관리자는 로그인과 토큰을 주고받는 방식이 달라 그룹마다 따로 안내하고,
 * 응답 형식·요청 제한·오류 코드는 함께 붙인다. 오류 코드 표는 ErrorCode에서 만들어 코드와 문서가 어긋나지 않는다.
 */
final class OpenApiGuide {

	private OpenApiGuide() {
	}

	static final String WEB = """
			고객 웹(Next.js)이 쓰는 API입니다. 오른쪽 위 목록에서 웹·앱·관리자 API를 고릅니다.

			### 로그인 (웹)
			1. `POST /api/v1/auth/login`으로 로그인하면 `data.accessToken`(15분)을 본문으로, Refresh 토큰을 \
			HttpOnly 쿠키 `refresh_token`(Path=/api/v1/auth)으로 받습니다. 스크립트는 Refresh 토큰을 읽을 수 없습니다.
			2. `data.accessToken`을 **Authorize**에 넣으면 이후 요청에 `Authorization: Bearer …`가 붙습니다.
			3. Access 토큰이 만료되면 `POST /api/v1/auth/refresh`로 다시 받습니다. 쿠키는 브라우저가 실어 보내고 Refresh 토큰도 \
			새것으로 바뀝니다. 재발급·로그아웃은 `CORS_ALLOWED_ORIGINS`에 있는 화면 주소에서 온 요청만 받습니다.
			4. 로그아웃은 `POST /api/v1/auth/logout`(쿠키와 Authorization 헤더)입니다.
			""";

	static final String APP = """
			모바일 앱이 쓰는 API입니다. 오른쪽 위 목록에서 웹·앱·관리자 API를 고릅니다.

			### 로그인 (앱)
			1. `POST /api/v1/app/auth/login`으로 로그인하면 `accessToken`(15분)과 `refreshToken`을 모두 응답 본문으로 받습니다(쿠키 없음).
			2. Refresh 토큰은 기기 보안 저장소(iOS Keychain, Android Keystore)에 두고 로그·화면·일반 저장소에 남기지 않습니다.
			3. Access 토큰이 만료되면 `POST /api/v1/app/auth/refresh`에 `{"refreshToken": "…"}`를 보내 새 토큰 쌍을 받고, \
			저장한 Refresh 토큰을 바꿉니다. 한 번 쓴 Refresh 토큰은 다시 쓸 수 없습니다(다시 보내면 401).
			4. 로그아웃은 `POST /api/v1/app/auth/logout`에 `{"refreshToken": "…"}`와 Authorization 헤더를 보냅니다.
			5. 앱 인증 API는 브라우저 요청(Origin 헤더가 있는 요청)을 403으로 거절합니다. 이 화면(Swagger UI)도 브라우저라서 \
			앱 인증 API는 앱이나 curl로 호출합니다. 다른 API는 웹 로그인으로 받은 Access 토큰을 **Authorize**에 넣어 확인할 수 있습니다.
			6. 회원가입·이메일 인증·비밀번호 재설정은 웹과 같은 `/api/v1/auth` API를 씁니다.
			""";

	static final String ADMIN = """
			관리자 화면(Vite)이 쓰는 API입니다. 오른쪽 위 목록에서 웹·앱·관리자 API를 고릅니다.

			### 로그인 (관리자)
			1. `POST /api/v1/admin/auth/login`(비밀번호)으로 로그인합니다. OTP를 쓰면 `mfaToken`을 받고, \
			`POST /api/v1/admin/auth/mfa/verify`(OTP를 처음 등록하면 `/mfa/setup`부터)로 토큰을 받습니다.
			2. `data.accessToken`을 **Authorize**에 넣습니다. Refresh 토큰은 HttpOnly 쿠키 `admin_refresh_token`\
			(Path=/api/v1/admin/auth)이고, 재발급·로그아웃은 `CORS_ADMIN_ORIGINS`의 관리자 화면 주소에서 온 요청만 받습니다.
			3. 관리자 세션은 30분 동안 쓰지 않거나 로그인한 지 8시간이 지나면 끝납니다.
			4. 관리자 API의 변경 요청과 개인정보가 보이는 조회는 감사 로그에 남습니다.
			""";

	/** 묶지 않은 전체 문서(/v3/api-docs)의 안내 */
	static final String ALL = """
			이음 쇼핑몰 백엔드 API 전체입니다. 웹·앱·관리자별 로그인 방법은 그룹 문서(웹 API, 앱 API, 관리자 API)에 있습니다.
			""";

	private static final String COMMON = """

			### 토큰 표시
			- 자물쇠가 없는 API는 토큰을 읽지 않습니다(로그인·재발급 등).
			- 설명에 "로그인 없이 부를 수 있다"고 적힌 API는 토큰이 선택입니다. 붙이면 로그인한 회원으로 처리하고, 만료된 토큰을 붙이면 401입니다.
			- 나머지는 토큰이 필요합니다.
			- 재발급·로그아웃·앱 인증 API는 요청 출처(Origin)를 확인하므로 이 화면에서 부르면 403일 수 있습니다.

			### 응답 형식
			- 성공: `{"success": true, "code": "SUCCESS", "message": "OK", "data": …}`
			- 실패: `{"success": false, "code": "오류 코드", "message": "안내 문구", "errors": [{"field": "…", "reason": "…"}]}`. \
			화면은 `code`로 분기합니다. `errors`는 입력값 검증 실패(400 INVALID_INPUT)일 때만 채웁니다.
			- API마다 성공 응답 아래에 일어날 수 있는 실패 응답(400·401·403·404·429)을 적어 두었습니다.

			### 요청 제한
			로그인·인증번호·문의 접수·주문·결제 등은 IP별 분당 요청 수를 제한합니다. 넘으면 429와 `Retry-After`(초)를 돌려줍니다.
			""";

	static String describe(String clientGuide) {
		return clientGuide + COMMON + errorCodes();
	}

	/** ErrorCode 전체를 표로 만든다. */
	static String errorCodes() {
		return Arrays.stream(ErrorCode.values())
				.map(code -> "| `" + code.name() + "` | " + code.getStatus().value() + " | "
						+ code.getMessage().replace("|", "\\|") + " |")
				.collect(Collectors.joining("\n", "\n### 오류 코드\n| code | HTTP | 안내 문구 |\n|---|---|---|\n", "\n"));
	}
}
