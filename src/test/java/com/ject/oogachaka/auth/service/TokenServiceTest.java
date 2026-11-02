package com.ject.oogachaka.auth.service;

import static com.ject.oogachaka.global.crypto.TestCryptoKeys.dummyKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

/** 실제 JWT 검증과 목 토큰 저장소로 갱신 거부·소유자 검사를 확인한다. 실제 Redis의 교체 원자성은 통합 테스트에서 검증한다. */
class TokenServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
    private final JwtTokenProvider jwt = new JwtTokenProvider(
        new JwtProperties(dummyKey(4), Duration.ofMinutes(30), Duration.ofDays(14), "oogachaka"), clock);
    private final AuthTokenStore store = mock(AuthTokenStore.class);
    private final TokenService service = new TokenService(jwt, store);

    // 저장소가 refresh 교체를 거부하면 새 토큰을 응답하지 않고 AUTH_REFRESH_REUSED를 발생시킨다.
    // 저장소의 거부 결과만 모사하며 토큰 재사용 자체를 Redis에서 재현하는 테스트는 아니다.
    @Test
    void rejectedRotationDoesNotReturnNewTokens() {
        TokenPair pair = jwt.issuePair(42L);
        when(store.rotate(eq(pair.refresh().claims()), any())).thenReturn(false);

        assertThatThrownBy(() -> service.refresh(pair.refresh().value()))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_REFRESH_REUSED));
    }

    // 다른 사용자의 refresh로 로그아웃하거나 access를 갱신용으로 제출하면 저장소에 접근하기 전에 거부해야 한다.
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
