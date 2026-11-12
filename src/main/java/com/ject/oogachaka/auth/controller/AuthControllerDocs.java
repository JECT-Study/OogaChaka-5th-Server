package com.ject.oogachaka.auth.controller;

import com.ject.oogachaka.auth.dto.AuthorizationResponse;
import com.ject.oogachaka.auth.dto.RefreshTokenRequest;
import com.ject.oogachaka.auth.dto.SocialLoginRequest;
import com.ject.oogachaka.auth.dto.SocialLoginResponse;
import com.ject.oogachaka.auth.dto.TokenResponse;
import com.ject.oogachaka.global.config.OpenApiConfig;
import com.ject.oogachaka.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** AuthController의 Swagger 문서. 컨트롤러가 이 인터페이스를 구현하면 springdoc이 여기 어노테이션을 읽는다. */
@Tag(name = "인증", description = "소셜 로그인, 토큰 갱신, 로그아웃. 오류 코드는 docs/auth-api.md를 참고한다.")
public interface AuthControllerDocs {

    @Operation(summary = "소셜 로그인 시작",
        description = "제공자 로그인 페이지 URL과 state를 발급한다. 프런트는 state를 보관했다가 콜백으로 돌아온 state와 같은지 비교한다.")
    ApiResponse<AuthorizationResponse> authorize(
        @Parameter(description = "소셜 제공자. 대소문자를 구분하지 않는다.", example = "kakao, google, naver") String provider);

    @Operation(summary = "소셜 로그인 완료",
        description = "제공자 콜백 URL로 받은 code와 state로 로그인하고 앱 토큰을 발급한다. 첫 로그인이면 자동 가입한다.\n\n"
            + "provider는 GOOGLE, KAKAO, NAVER 중 하나를 대문자로 보낸다(소문자는 400).")
    ApiResponse<SocialLoginResponse> socialLogin(SocialLoginRequest request);

    @Operation(summary = "토큰 갱신",
        description = "refresh로 새 access·refresh를 발급한다. 보낸 refresh는 더 이상 쓸 수 없으므로 응답의 refresh로 바로 교체한다.")
    ApiResponse<TokenResponse> refresh(RefreshTokenRequest request);

    @Operation(summary = "로그아웃",
        description = "이 기기의 refresh를 무효로 만든다. 이미 발급된 access는 만료 시각까지 유효하다.",
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    ApiResponse<Void> logout(@Parameter(hidden = true) Long userId, RefreshTokenRequest request);
}
