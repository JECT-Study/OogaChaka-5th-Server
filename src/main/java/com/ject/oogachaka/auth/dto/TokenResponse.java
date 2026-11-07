package com.ject.oogachaka.auth.dto;

import com.ject.oogachaka.auth.token.TokenPair;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;

// 프런트가 Authorization 헤더를 만들 tokenType과 우리 서비스의 두 JWT 문자열을 반환한다. 외부 소셜 제공자 토큰은 여기에 넣지 않는다.
public record TokenResponse(
    @Schema(description = "Authorization 헤더에 붙일 인증 방식", example = "Bearer")
    String tokenType,
    @Schema(description = "보호 API 호출에 쓰는 access 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
    String accessToken,
    @Schema(description = "access가 만료됐을 때 갱신에 쓰는 refresh 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
    String refreshToken,
    @Schema(description = "access 수명(초)", example = "1800")
    long accessTokenExpiresIn,
    @Schema(description = "refresh 수명(초)", example = "1209600")
    long refreshTokenExpiresIn) {

    public static TokenResponse from(TokenPair tokens) {
        return new TokenResponse("Bearer", tokens.access().value(), tokens.refresh().value(),
            Duration.between(tokens.access().claims().issuedAt(), tokens.access().claims().expiresAt()).toSeconds(),
            Duration.between(tokens.refresh().claims().issuedAt(), tokens.refresh().claims().expiresAt()).toSeconds());
    }
}
