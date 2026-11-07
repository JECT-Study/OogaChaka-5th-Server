package com.ject.oogachaka.global.config;

import static com.ject.oogachaka.global.crypto.TestCryptoKeys.dummyKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** JWT 키의 설정 검증과 Spring 바인딩을 확인한다. 실제 토큰의 서명·만료 검증은 JwtTokenProviderTest 범위다. */
class JwtPropertiesTest {

    // JWT 서명 키가 누락되거나 Base64 형식·최소 32바이트 조건을 어기면 설정 생성을 거부하고 원래 예외를 원인으로 전달하지 않는다.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-base64!", "ZHVtbXk="})
    void rejectsMissingMalformedAndShortKey(String secret) {
        assertThatThrownBy(() -> new JwtProperties(secret, Duration.ofMinutes(30), Duration.ofDays(14)))
            .isInstanceOf(IllegalStateException.class).hasNoCause();
    }

    // 정상 JWT 설정은 서명 키 빈으로 바인딩되고 secret이 없으면 애플리케이션 시작 단계에서 실패해야 한다.
    @Test
    void bindsAtStartupAndFailsWithoutSecret() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class)
            .withPropertyValues("app.jwt.access-token-ttl=30m", "app.jwt.refresh-token-ttl=14d");
        runner.withPropertyValues("app.jwt.secret=" + dummyKey(4)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtProperties.class).getSigningKey().getEncoded()).hasSize(32);
        });
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesConfig {
    }
}
