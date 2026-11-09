package com.example.shopping.domain.member.dto.response;

/**
 * 관리자 로그인 1단계(비밀번호) 결과(#164).
 * <ul>
 * <li>OTP를 요구하면: mfaRequired=true, mfaToken(5분, 1회용). OTP를 아직 등록하지 않았으면 mfaSetupRequired=true이고,
 * POST /api/v1/admin/auth/mfa/setup으로 등록 값을 받은 뒤 /mfa/verify에 코드를 보낸다. Access 토큰은 /mfa/verify가 준다.</li>
 * <li>OTP를 요구하지 않으면(ADMIN_MFA_REQUIRED=false이고 등록하지 않은 관리자): 회원 로그인과 같이 accessToken을 준다.</li>
 * </ul>
 */
public record AdminLoginResponse(
		boolean mfaRequired,
		boolean mfaSetupRequired,
		String mfaToken,
		Long mfaTokenExpiresIn,
		String accessToken,
		Long expiresIn) {

	public static AdminLoginResponse mfa(String mfaToken, long expiresIn, boolean setupRequired) {
		return new AdminLoginResponse(true, setupRequired, mfaToken, expiresIn, null, null);
	}

	public static AdminLoginResponse authenticated(String accessToken, long expiresIn) {
		return new AdminLoginResponse(false, false, null, null, accessToken, expiresIn);
	}
}
