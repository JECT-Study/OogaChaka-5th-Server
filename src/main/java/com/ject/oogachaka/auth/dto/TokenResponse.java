package com.ject.oogachaka.auth.dto;

import com.ject.oogachaka.auth.token.TokenPair;
import java.time.Duration;

public record TokenResponse(String tokenType, String accessToken, String refreshToken,
    long accessTokenExpiresIn, long refreshTokenExpiresIn) {

    public static TokenResponse from(TokenPair tokens) {
        return new TokenResponse("Bearer", tokens.access().value(), tokens.refresh().value(),
            Duration.between(tokens.access().claims().issuedAt(), tokens.access().claims().expiresAt()).toSeconds(),
            Duration.between(tokens.refresh().claims().issuedAt(), tokens.refresh().claims().expiresAt()).toSeconds());
    }

    @Override
    public String toString() {
        return "TokenResponse[REDACTED]";
    }
}
