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

class JwtTokenProviderTest {

    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private final JwtProperties properties = new JwtProperties(dummyKey(4), Duration.ofMinutes(30), Duration.ofDays(14), "oogachaka");
    private final JwtTokenProvider provider = new JwtTokenProvider(properties, clockAt(NOW));

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

    @Test
    void rejectsExpiredTokenWithDistinctCodeIncludingExactExpiry() {
        String token = provider.issuePair(42L).access().value();
        JwtTokenProvider future = new JwtTokenProvider(properties, clockAt(NOW.plus(Duration.ofMinutes(30))));

        assertError(() -> future.validate(token, TokenType.ACCESS), ErrorCode.AUTH_TOKEN_EXPIRED);
    }

    @Test
    void allowsFiveSecondsOfIssuedAtClockSkewButRejectsMore() {
        JwtTokenProvider fiveSecondsAhead = new JwtTokenProvider(properties, clockAt(NOW.plusSeconds(5)));
        String allowed = fiveSecondsAhead.issuePair(42L).access().value();
        assertThat(provider.validate(allowed, TokenType.ACCESS).issuedAt()).isEqualTo(NOW.plusSeconds(5));

        JwtTokenProvider sixSecondsAhead = new JwtTokenProvider(properties, clockAt(NOW.plusSeconds(6)));
        assertError(() -> provider.validate(sixSecondsAhead.issuePair(42L).access().value(), TokenType.ACCESS),
            ErrorCode.AUTH_TOKEN_INVALID);
    }

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
