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

/** HS256 서명과 발급자, 토큰 종류, 필수 클레임을 함께 검증한다. */
@Component
public class JwtTokenProvider {

    private static final int MAX_TOKEN_LENGTH = 4096;
    private final JwtProperties properties;
    private final Clock clock;
    private final JwtParser parser;

    public JwtTokenProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.parser = Jwts.parser().verifyWith(properties.getSigningKey())
            .requireIssuer(properties.getIssuer()).clock(() -> Date.from(clock.instant()))
            .sig().clear().add(Jwts.SIG.HS256).and()
            .zip().clear().and().build();
    }

    public TokenPair issuePair(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("양수 userId가 필요합니다.");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return new TokenPair(issue(userId, TokenType.ACCESS, now, properties.getAccessTokenTtl()),
            issue(userId, TokenType.REFRESH, now, properties.getRefreshTokenTtl()));
    }

    public JwtClaims validate(String token, TokenType expectedType) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw invalidToken();
        }
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            Date issuedAt = claims.getIssuedAt();
            Date expiresAt = claims.getExpiration();
            String jti = claims.getId();
            if (userId <= 0 || !userId.toString().equals(claims.getSubject())
                || !expectedType.getClaim().equals(claims.get("type", String.class))
                || jti == null || !UUID.fromString(jti).toString().equals(jti)
                || issuedAt == null || expiresAt == null || !expiresAt.after(issuedAt)
                || issuedAt.toInstant().isAfter(clock.instant())) {
                throw invalidToken();
            }
            if (!expiresAt.toInstant().isAfter(clock.instant())) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
            }
            return new JwtClaims(userId, jti, expectedType, issuedAt.toInstant(), expiresAt.toInstant());
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            // 파서 예외에는 원문/클레임이 포함될 수 있으므로 원인을 전달하지 않는다.
            throw invalidToken();
        }
    }

    private IssuedToken issue(Long userId, TokenType type, Instant now, Duration ttl) {
        String jti = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder().subject(userId.toString()).id(jti).claim("type", type.getClaim())
            .issuer(properties.getIssuer()).issuedAt(Date.from(now)).expiration(Date.from(expiresAt))
            .signWith(properties.getSigningKey(), Jwts.SIG.HS256).compact();
        return new IssuedToken(token, new JwtClaims(userId, jti, type, now, expiresAt));
    }

    private BusinessException invalidToken() {
        return new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
    }
}
