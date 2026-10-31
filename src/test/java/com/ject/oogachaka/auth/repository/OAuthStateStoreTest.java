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
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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

    @Test
    void storesWithTtlAndConsumesOnceUsingGetdel() {
        when(values.setIfAbsent(AuthRedisKeys.state(STATE), "GOOGLE:" + VERIFIER, Duration.ofMinutes(5))).thenReturn(true);
        assertThat(store.save(STATE, new OAuthState(AuthProvider.GOOGLE, VERIFIER))).isTrue();
        when(values.getAndDelete(AuthRedisKeys.state(STATE))).thenReturn("GOOGLE:" + VERIFIER, (String) null);
        assertThat(store.consume(STATE, AuthProvider.GOOGLE).verifier()).isEqualTo(VERIFIER);
        assertInvalid(() -> store.consume(STATE, AuthProvider.GOOGLE));
    }

    @Test
    void rejectsProviderMismatchAndUnknownState() {
        when(values.getAndDelete(AuthRedisKeys.state(STATE))).thenReturn("KAKAO:" + VERIFIER, (String) null);
        assertInvalid(() -> store.consume(STATE, AuthProvider.GOOGLE));
        assertInvalid(() -> store.consume(STATE, AuthProvider.GOOGLE));
        assertInvalid(() -> store.consume("invalid-format", AuthProvider.GOOGLE));
    }

    @Test
    void redisFailureIsSanitizedAndDoesNotAcceptState() {
        when(values.getAndDelete(AuthRedisKeys.state(STATE)))
            .thenThrow(new RedisConnectionFailureException("TEST_ONLY_DRIVER_DETAILS"));
        assertThatThrownBy(() -> store.consume(STATE, AuthProvider.GOOGLE)).isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_STORE_UNAVAILABLE)).hasNoCause();
    }

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_STATE_INVALID));
    }
}
