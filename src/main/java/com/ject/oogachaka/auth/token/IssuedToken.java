package com.ject.oogachaka.auth.token;

// 발급한 JWT 문자열과 그 클레임을 같이 보관해 Redis 저장·응답 수명 계산을 위해 문자열을 다시 파싱하지 않게 한다.
public record IssuedToken(String value, JwtClaims claims) {
}
