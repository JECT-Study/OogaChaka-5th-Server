package com.ject.oogachaka.auth.token;

public record IssuedToken(String value, JwtClaims claims) {
    @Override
    public String toString() {
        return "IssuedToken[REDACTED]";
    }
}
