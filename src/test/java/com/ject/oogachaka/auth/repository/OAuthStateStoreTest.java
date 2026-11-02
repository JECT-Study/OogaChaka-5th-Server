package com.ject.oogachaka.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.auth.dto.OAuthState;
import com.ject.oogachaka.global.config.OAuthProperties;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** Redis를 목 객체로 대체해 state 저장 실패·조회 장애의 오류 처리를 확인한다. 실제 일회성 소비는 Redis 통합 테스트에서 확인한다. */
class OAuthStateStoreTest {

    private static final String STATE = "s".repeat(43);
    private static final String VERIFIER = "v".repeat(43);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final OAuthProperties properties = mock(OAuthProperties.class);
    private final OAuthStateStore store = new OAuthStateStore(redis, properties);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(properties.getStateTtl()).thenReturn(Duration.ofMinutes(5));
    }

    // Redis 저장 결과가 false 또는 null이면 인가 URL 발급을 계속하지 않도록 AUTH_STORE_UNAVAILABLE을 발생시킨다.
    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void failedSaveIsRejectedByTheStore(Boolean stored) {
        when(values.setIfAbsent(AuthRedisKeys.state(STATE), "GOOGLE:" + VERIFIER, Duration.ofMinutes(5))).thenReturn(stored);
        assertThatThrownBy(() -> store.save(STATE, new OAuthState(AuthProvider.GOOGLE, VERIFIER)))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_STORE_UNAVAILABLE));
    }

    // state 조회 중 Redis 연결이 끊기면 AUTH_STORE_UNAVAILABLE로 중단하고 드라이버 예외 원인을 외부에 전달하지 않는다.
    @Test
    void redisFailureIsSanitizedAndDoesNotAcceptState() {
        when(values.getAndDelete(AuthRedisKeys.state(STATE)))
            .thenThrow(new RedisConnectionFailureException("TEST_ONLY_DRIVER_DETAILS"));
        assertThatThrownBy(() -> store.consume(STATE, AuthProvider.GOOGLE)).isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_STORE_UNAVAILABLE)).hasNoCause();
    }
}
