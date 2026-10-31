package com.ject.oogachaka.auth.token;

import com.ject.oogachaka.auth.enums.TokenType;
import java.time.Instant;

public record JwtClaims(Long userId, String jti, TokenType type, Instant issuedAt, Instant expiresAt) {
}
