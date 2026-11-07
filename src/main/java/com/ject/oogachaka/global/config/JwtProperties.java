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
    // [검토] 보호 API에 쓰는 access 수명이다. 기본 30분은 프로젝트 정책이며 OAuth·JWT 표준이 강제하는 값은 아니다.
    private final Duration accessTokenTtl;
    // [검토] 재로그인 없이 갱신 가능한 refresh 수명이다. 기본 14일 역시 프로젝트 선택이다.
    private final Duration refreshTokenTtl;

    public JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
        try {
            this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } catch (RuntimeException exception) {
            // 원래 예외 메시지에 키 일부가 담길 수 있어 원인을 넘기지 않는다.
            throw new IllegalStateException("JWT_SECRET은 Base64로 인코딩한 32바이트 이상 키여야 합니다.");
        }
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }
}
