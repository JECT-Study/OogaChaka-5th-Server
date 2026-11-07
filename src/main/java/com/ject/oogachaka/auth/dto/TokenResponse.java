package com.ject.oogachaka.auth.dto;

import com.ject.oogachaka.auth.token.TokenPair;
import java.time.Duration;

// 프런트가 Authorization 헤더를 만들 tokenType과 우리 서비스의 두 JWT 문자열을 반환한다. 외부 소셜 제공자 토큰은 여기에 넣지 않는다.
public record TokenResponse(
    String tokenType,
    String accessToken,
    String refreshToken,
    long accessTokenExpiresIn,
    long refreshTokenExpiresIn) {

    public static TokenResponse from(TokenPair tokens) {
        return new TokenResponse("Bearer", tokens.access().value(), tokens.refresh().value(),
            Duration.between(tokens.access().claims().issuedAt(), tokens.access().claims().expiresAt()).toSeconds(),
            Duration.between(tokens.refresh().claims().issuedAt(), tokens.refresh().claims().expiresAt()).toSeconds());
    }
}
