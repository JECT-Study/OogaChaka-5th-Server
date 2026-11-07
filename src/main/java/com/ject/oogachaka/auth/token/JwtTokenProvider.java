package com.ject.oogachaka.auth.token;

import com.ject.oogachaka.auth.enums.TokenType;
import com.ject.oogachaka.global.config.JwtProperties;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** HS256으로 JWT를 발급하고 서명·만료·토큰 종류를 검증한다. */
@Component
public class JwtTokenProvider {

    private final JwtProperties properties;
    private final Clock clock;
    private final JwtParser parser;

    public JwtTokenProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        // 서버 비밀 키로 서명을 검증하고, 만료 검사에도 주입한 시계를 쓴다.
        this.parser = Jwts.parser().verifyWith(properties.getSigningKey()).clock(() -> Date.from(clock.instant())).build();
    }

    // 호출자가 기대하는 종류까지 검증해 access·refresh가 같은 서명 키를 사용해도 용도를 구분한다.
    public JwtClaims validate(String token, TokenType expectedType) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();

            // 같은 키로 서명한 14일짜리 refresh를 access로 쓰지 못하게 종류를 확인한다.
            if (!expectedType.getClaim().equals(claims.get("type", String.class))) {
                throw invalidToken();
            }

            return new JwtClaims(Long.valueOf(claims.getSubject()), claims.getId(), expectedType,
                claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant());
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    public TokenPair issuePair(Long userId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return new TokenPair(issue(userId, TokenType.ACCESS, now, properties.getAccessTokenTtl()),
                issue(userId, TokenType.REFRESH, now, properties.getRefreshTokenTtl()));
    }

    // access와 refresh에서 공통인 서명·표준 클레임 생성을 한 곳에 둔다.
    private IssuedToken issue(Long userId, TokenType type, Instant now, Duration ttl) {
        // 발급마다 다른 토큰 ID를 만든다. 같은 사용자도 여러 기기·갱신마다 별도의 Redis 키를 갖게 한다.
        String jti = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder().subject(userId.toString()).id(jti).claim("type", type.getClaim())
            .issuedAt(Date.from(now)).expiration(Date.from(expiresAt))
            .signWith(properties.getSigningKey(), Jwts.SIG.HS256).compact();
        return new IssuedToken(token, new JwtClaims(userId, jti, type, now, expiresAt));
    }

    private BusinessException invalidToken() {
        return new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
    }
}
