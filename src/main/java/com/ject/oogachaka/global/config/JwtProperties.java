package com.ject.oogachaka.global.config;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** JWT 서명 키와 수명을 시작 단계에서 검증한다. 키의 문자열 표현을 제공하지 않는다. */
@Getter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final String issuer;

    public JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl, String issuer) {
        byte[] decoded;
        try {
            if (secret == null || secret.isBlank()) {
                throw new IllegalArgumentException();
            }
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET은 유효한 Base64 서명 키여야 합니다.");
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("JWT_SECRET은 Base64 디코딩 후 최소 32바이트여야 합니다.");
        }
        validateTtl(accessTokenTtl);
        validateTtl(refreshTokenTtl);
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("app.jwt.issuer가 필요합니다.");
        }
        this.signingKey = new SecretKeySpec(decoded, "HmacSHA256");
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.issuer = issuer;
    }

    private static void validateTtl(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.getNano() != 0) {
            throw new IllegalStateException("JWT 수명은 양의 정수 초여야 합니다.");
        }
    }
}
