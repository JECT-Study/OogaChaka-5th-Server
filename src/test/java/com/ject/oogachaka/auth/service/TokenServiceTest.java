package com.ject.oogachaka.auth.service;

import static com.ject.oogachaka.global.crypto.TestCryptoKeys.dummyKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.auth.dto.TokenResponse;
import com.ject.oogachaka.auth.enums.TokenType;
import com.ject.oogachaka.auth.repository.AuthTokenStore;
import com.ject.oogachaka.auth.token.JwtTokenProvider;
import com.ject.oogachaka.auth.token.TokenPair;
import com.ject.oogachaka.global.config.JwtProperties;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import com.ject.oogachaka.auth.token.JwtClaims;

class TokenServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
    private final JwtTokenProvider jwt = new JwtTokenProvider(
        new JwtProperties(dummyKey(4), Duration.ofMinutes(30), Duration.ofDays(14), "oogachaka"), clock);
    private final AuthTokenStore store = mock(AuthTokenStore.class);
    private final TokenService service = new TokenService(jwt, store);

    @Test
    void issueRegistersRefreshAndRotationReturnsNewIds() {
        TokenResponse initial = service.issue(42L);
        JwtClaims previous = jwt.validate(initial.refreshToken(), TokenType.REFRESH);
        verify(store).saveRefresh(previous);
        when(store.rotate(eq(previous), any())).thenReturn(true);

        TokenResponse result = service.refresh(initial.refreshToken());

        JwtClaims next = jwt.validate(result.refreshToken(), TokenType.REFRESH);
        verify(store).rotate(previous, next);
        assertThat(next.userId()).isEqualTo(42L);
        assertThat(next.jti()).isNotEqualTo(previous.jti());
        assertThat(result.accessToken()).isNotEqualTo(initial.accessToken());
    }

    @Test
    void reuseOrMissingRefreshIsRejected() {
        TokenPair pair = jwt.issuePair(42L);
        when(store.rotate(eq(pair.refresh().claims()), any())).thenReturn(true, false);
        service.refresh(pair.refresh().value());

        assertThatThrownBy(() -> service.refresh(pair.refresh().value()))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_REFRESH_REUSED));
    }

    @Test
    void cannotLogoutAnotherUsersRefreshOrUseAccessAsRefresh() {
        TokenPair pair = jwt.issuePair(42L);
        TokenPair other = jwt.issuePair(99L);
        assertThatThrownBy(() -> service.logout(pair.access().claims(), other.refresh().value()))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
        assertThatThrownBy(() -> service.refresh(pair.access().value())).isInstanceOf(BusinessException.class);
        verifyNoInteractions(store);
    }
}
