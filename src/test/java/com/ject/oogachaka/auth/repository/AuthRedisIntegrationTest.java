package com.ject.oogachaka.auth.repository;

import static com.ject.oogachaka.global.crypto.TestCryptoKeys.dummyKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.auth.dto.OAuthState;
import com.ject.oogachaka.auth.token.JwtTokenProvider;
import com.ject.oogachaka.auth.token.TokenPair;
import com.ject.oogachaka.global.config.JwtProperties;
import com.ject.oogachaka.global.config.OAuthProperties;
import com.ject.oogachaka.global.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 실제 Redis의 GETDEL/Lua/TTL/동시성 검증용. 이번 작업에서는 작성·컴파일만 한다. */
@Testcontainers
class AuthRedisIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
        .withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
    private final JwtTokenProvider tokens = new JwtTokenProvider(
        new JwtProperties(dummyKey(4), Duration.ofMinutes(30), Duration.ofDays(14), "oogachaka"), clock);

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void parallelRotationHasOneWinnerAndOldRefreshCannotBeReused() throws Exception {
        AuthTokenStore store = new AuthTokenStore(redis, clock);
        TokenPair original = tokens.issuePair(42L);
        TokenPair first = tokens.issuePair(42L);
        TokenPair second = tokens.issuePair(42L);
        store.saveRefresh(original.refresh().claims());
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> { start.await(); return store.rotate(original.refresh().claims(), first.refresh().claims()); });
            var b = executor.submit(() -> { start.await(); return store.rotate(original.refresh().claims(), second.refresh().claims()); });
            start.countDown();
            boolean firstWon = a.get(10, TimeUnit.SECONDS);
            boolean secondWon = b.get(10, TimeUnit.SECONDS);
            assertThat(List.of(firstWon, secondWon)).containsExactlyInAnyOrder(true, false);
            assertThat(store.rotate(original.refresh().claims(), tokens.issuePair(42L).refresh().claims())).isFalse();
            assertThat(redis.hasKey(AuthRedisKeys.refresh(42L, original.refresh().claims().jti()))).isFalse();
            TokenPair winner = firstWon ? first : second;
            assertThat(redis.opsForValue().get(AuthRedisKeys.refresh(42L, winner.refresh().claims().jti()))).isEqualTo("42");
            assertThat(redis.getExpire(AuthRedisKeys.refresh(42L, winner.refresh().claims().jti()), TimeUnit.SECONDS))
                .isBetween(1209590L, 1209600L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void logoutRevokesRefreshAndOnlyCurrentAccessWhileOtherDeviceSurvives() {
        AuthTokenStore store = new AuthTokenStore(redis, clock);
        TokenPair current = tokens.issuePair(77L);
        TokenPair anotherDevice = tokens.issuePair(77L);
        store.saveRefresh(current.refresh().claims());
        store.saveRefresh(anotherDevice.refresh().claims());
        store.logout(current.refresh().claims(), current.access().claims());

        assertThat(store.isBlacklisted(current.access().claims())).isTrue();
        assertThat(store.isBlacklisted(anotherDevice.access().claims())).isFalse();
        assertThat(redis.hasKey(AuthRedisKeys.refresh(77L, current.refresh().claims().jti()))).isFalse();
        assertThat(redis.hasKey(AuthRedisKeys.refresh(77L, anotherDevice.refresh().claims().jti()))).isTrue();
        assertThat(redis.getExpire(AuthRedisKeys.blacklist(77L, current.access().claims().jti()), TimeUnit.SECONDS))
            .isBetween(1790L, 1800L);
    }

    @Test
    void stateTtlAndGetdelPreventReplayAndProviderMixup() {
        OAuthStateStore states = new OAuthStateStore(redis, new OAuthProperties(Duration.ofMinutes(5),
            Duration.ofSeconds(3), Duration.ofSeconds(5), Map.of()));
        String state = "s".repeat(43);
        states.save(state, new OAuthState(AuthProvider.GOOGLE, "v".repeat(43)));
        assertThat(redis.getExpire(AuthRedisKeys.state(state), TimeUnit.SECONDS)).isBetween(290L, 300L);
        assertThat(states.consume(state, AuthProvider.GOOGLE).verifier()).isEqualTo("v".repeat(43));
        assertThatThrownBy(() -> states.consume(state, AuthProvider.GOOGLE)).isInstanceOf(BusinessException.class);
        states.save(state, new OAuthState(AuthProvider.KAKAO, "v".repeat(43)));
        assertThatThrownBy(() -> states.consume(state, AuthProvider.GOOGLE)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> states.consume(state, AuthProvider.KAKAO)).isInstanceOf(BusinessException.class);
    }
}
