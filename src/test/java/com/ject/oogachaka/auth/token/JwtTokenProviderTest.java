package com.ject.oogachaka.auth.token;

import static com.ject.oogachaka.global.crypto.TestCryptoKeys.dummyKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ject.oogachaka.auth.enums.TokenType;
import com.ject.oogachaka.global.config.JwtProperties;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** 고정 시계와 테스트 키로 JWT 발급 규칙과 위조·만료·토큰 종류 검증을 확인한다. 외부 서버를 사용하지 않는다. */
class JwtTokenProviderTest {

    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private final JwtProperties properties = new JwtProperties(dummyKey(4), Duration.ofMinutes(30), Duration.ofDays(14));
    private final JwtTokenProvider provider = new JwtTokenProvider(properties, clockAt(NOW));

    // 발급한 access·refresh를 다시 검증해 사용자 ID, 발급 시각, 각 수명과 서로 다른 jti가 유지되는지 확인한다.
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
    }

    // access 만료 시각이 지난 뒤 검증하면 일반 무효 토큰과 구분되는 AUTH_TOKEN_EXPIRED로 거부한다.
    @Test
    void rejectsExpiredTokenWithDistinctCode() {
        String token = provider.issuePair(42L).access().value();
        JwtTokenProvider future = new JwtTokenProvider(properties, clockAt(NOW.plus(Duration.ofMinutes(30)).plusSeconds(1)));

        assertError(() -> future.validate(token, TokenType.ACCESS), ErrorCode.AUTH_TOKEN_EXPIRED);
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
            Duration.ofDays(14)), clockAt(NOW));
        assertError(() -> provider.validate(other.issuePair(42L).access().value(), TokenType.ACCESS), ErrorCode.AUTH_TOKEN_INVALID);
    }

    // refresh를 access로 사용하거나 JWT 형식 자체가 깨진 문자열은 AUTH_TOKEN_INVALID로 거부한다.
    @Test
    void rejectsWrongTypeAndMalformedToken() {
        assertError(() -> provider.validate(provider.issuePair(42L).refresh().value(), TokenType.ACCESS), ErrorCode.AUTH_TOKEN_INVALID);
        assertError(() -> provider.validate("not-a-jwt", TokenType.ACCESS), ErrorCode.AUTH_TOKEN_INVALID);
    }

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(code)).hasNoCause();
    }
}
