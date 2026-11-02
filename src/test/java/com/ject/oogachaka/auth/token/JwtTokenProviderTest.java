package com.ject.oogachaka.auth.token;

import static com.ject.oogachaka.global.crypto.TestCryptoKeys.dummyKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ject.oogachaka.auth.enums.TokenType;
import com.ject.oogachaka.global.config.JwtProperties;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 고정 시계와 테스트 키로 JWT 발급 규칙, 위조·만료·필수 클레임 검증을 확인한다. 외부 서버를 사용하지 않는다. */
class JwtTokenProviderTest {

    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private final JwtProperties properties = new JwtProperties(dummyKey(4), Duration.ofMinutes(30), Duration.ofDays(14), "oogachaka");
    private final JwtTokenProvider provider = new JwtTokenProvider(properties, clockAt(NOW));

    // 발급한 access·refresh를 다시 검증해 사용자 ID, 발급 시각, 각 수명과 서로 다른 jti가 유지되는지 확인한다.
    // 토큰 묶음을 문자열로 출력해도 JWT 원문이 드러나지 않아야 한다.
    @Test
    void issuesRequiredClaimsAndIndependentAccessRefreshIds() {
        TokenPair pair = provider.issuePair(42L);
        JwtClaims access = provider.validate(pair.access().value(), TokenType.ACCESS);
        JwtClaims refresh = provider.validate(pair.refresh().value(), TokenType.REFRESH);

        assertThat(access.userId()).isEqualTo(42L);
        assertThat(access.issuedAt()).isEqualTo(NOW);
        assertThat(access.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(refresh.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
        assertThat(access.jti()).isNotEqualTo(refresh.jti());
        assertThat(pair.toString()).doesNotContain(pair.access().value(), pair.refresh().value());
    }

    // 현재 시각이 access의 만료 시각과 정확히 같으면 AUTH_TOKEN_EXPIRED로 거부한다. 만료 이후의 유예를 허용하지 않는다.
    @Test
    void rejectsExpiredTokenWithDistinctCodeIncludingExactExpiry() {
        String token = provider.issuePair(42L).access().value();
        JwtTokenProvider future = new JwtTokenProvider(properties, clockAt(NOW.plus(Duration.ofMinutes(30))));

        assertError(() -> future.validate(token, TokenType.ACCESS), ErrorCode.AUTH_TOKEN_EXPIRED);
    }

    // 다른 서버가 현재보다 5초 앞서 발급한 토큰은 허용하고, 6초 앞서 발급한 토큰은 AUTH_TOKEN_INVALID로 거부한다.
    @Test
    void allowsFiveSecondsOfIssuedAtClockSkewButRejectsMore() {
        JwtTokenProvider fiveSecondsAhead = new JwtTokenProvider(properties, clockAt(NOW.plusSeconds(5)));
        String allowed = fiveSecondsAhead.issuePair(42L).access().value();
        assertThat(provider.validate(allowed, TokenType.ACCESS).issuedAt()).isEqualTo(NOW.plusSeconds(5));

        JwtTokenProvider sixSecondsAhead = new JwtTokenProvider(properties, clockAt(NOW.plusSeconds(6)));
        assertError(() -> provider.validate(sixSecondsAhead.issuePair(42L).access().value(), TokenType.ACCESS),
            ErrorCode.AUTH_TOKEN_INVALID);
    }

    // 사용자 ID를 고친 payload에 기존 서명을 붙이거나 다른 키로 서명한 JWT는 모두 AUTH_TOKEN_INVALID로 거부한다.
    @Test
    void rejectsTamperedPayloadAndOtherSigningKey() {
        String token = provider.issuePair(42L).access().value();
        String[] parts = token.split("\\.");
        String changedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"sub\":\"999\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertError(() -> provider.validate(parts[0] + "." + changedPayload + "." + parts[2], TokenType.ACCESS),
            ErrorCode.AUTH_TOKEN_INVALID);
        JwtTokenProvider other = new JwtTokenProvider(new JwtProperties(dummyKey(5), Duration.ofMinutes(30),
            Duration.ofDays(14), "oogachaka"), clockAt(NOW));
        assertError(() -> provider.validate(other.issuePair(42L).access().value(), TokenType.ACCESS), ErrorCode.AUTH_TOKEN_INVALID);
    }

    // refresh를 access로 사용하거나 발급자·만료 시각·양수 사용자 ID 조건을 어긴 JWT는 서명이 맞아도 거부한다.
    // JWT 형식 자체가 깨진 문자열도 같은 AUTH_TOKEN_INVALID로 처리한다.
    @Test
    void rejectsWrongTypeIssuerMissingExpiryAndNonpositiveSubject() {
        assertError(() -> provider.validate(provider.issuePair(42L).refresh().value(), TokenType.ACCESS), ErrorCode.AUTH_TOKEN_INVALID);
        String wrongIssuer = signed("42", "another-issuer", true);
        assertError(() -> provider.validate(wrongIssuer, TokenType.ACCESS), ErrorCode.AUTH_TOKEN_INVALID);
        assertError(() -> provider.validate(signed("42", "oogachaka", false), TokenType.ACCESS), ErrorCode.AUTH_TOKEN_INVALID);
        assertError(() -> provider.validate(signed("0", "oogachaka", true), TokenType.ACCESS), ErrorCode.AUTH_TOKEN_INVALID);
        assertError(() -> provider.validate("not-a-jwt", TokenType.ACCESS), ErrorCode.AUTH_TOKEN_INVALID);
    }

    private String signed(String subject, String issuer, boolean expiry) {
        var builder = Jwts.builder().subject(subject).issuer(issuer).id(UUID.randomUUID().toString())
            .issuedAt(Date.from(NOW)).claim("type", "access");
        if (expiry) {
            builder.expiration(Date.from(NOW.plusSeconds(100)));
        }
        return builder.signWith(properties.getSigningKey(), Jwts.SIG.HS256).compact();
    }

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(code)).hasNoCause();
    }
}
