package com.ject.oogachaka.global.config;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import javax.crypto.SecretKey;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** JWT 서명 키와 수명. 키가 없거나 Base64가 아니거나 32바이트 미만이면 시작 단계에서 실패한다. */
@Getter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
        try {
            this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("JWT_SECRET은 Base64로 인코딩한 32바이트 이상 키여야 합니다.");
        }
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }
}
