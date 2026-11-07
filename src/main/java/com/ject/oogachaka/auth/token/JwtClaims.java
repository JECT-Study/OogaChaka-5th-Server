package com.ject.oogachaka.auth.token;

import com.ject.oogachaka.auth.enums.TokenType;
import java.time.Instant;

// 검증·발급 후 사용자 ID, 개별 토큰 ID, 용도, 발급·만료 시각을 전달하는 불변 값이다. 서명 문자열 없이 필터·Redis가 필요한 값을 공유한다.
public record JwtClaims(Long userId, String jti, TokenType type, Instant issuedAt, Instant expiresAt) {
}
