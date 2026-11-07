package com.ject.oogachaka.auth.service;

import static com.ject.oogachaka.global.crypto.TestCryptoKeys.dummyKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ject.oogachaka.auth.repository.AuthTokenStore;
import com.ject.oogachaka.auth.token.JwtTokenProvider;
import com.ject.oogachaka.global.config.JwtProperties;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** 실제 JWT 검증과 목 토큰 저장소로 로그아웃의 소유자 검사를 확인한다. */
class TokenServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
    private final JwtTokenProvider jwt = new JwtTokenProvider(
        new JwtProperties(dummyKey(4), Duration.ofMinutes(30), Duration.ofDays(14)), clock);
    private final AuthTokenStore store = mock(AuthTokenStore.class);
    private final TokenService service = new TokenService(jwt, store);

    // 42번 사용자가 99번 사용자의 refresh로 로그아웃하면 저장소에 접근하기 전에 AUTH_TOKEN_INVALID로 거부해야 한다.
    @Test
    void cannotLogoutAnotherUsersRefresh() {
        String otherRefresh = jwt.issuePair(99L).refresh().value();
        assertThatThrownBy(() -> service.logout(42L, otherRefresh))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
        verifyNoInteractions(store);
    }
}
