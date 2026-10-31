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

class JwtPropertiesTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-base64!", "ZHVtbXk="})
    void rejectsMissingMalformedAndShortKeyWithoutExposingInput(String secret) {
        assertThatThrownBy(() -> new JwtProperties(secret, Duration.ofMinutes(30), Duration.ofDays(14), "oogachaka"))
            .isInstanceOf(IllegalStateException.class).hasNoCause();
    }

    @Test
    void bindsAtStartupAndFailsWithoutSecret() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class)
            .withPropertyValues("app.jwt.access-token-ttl=30m", "app.jwt.refresh-token-ttl=14d", "app.jwt.issuer=oogachaka");
        runner.withPropertyValues("app.jwt.secret=" + dummyKey(4)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtProperties.class).getSigningKey().getEncoded()).hasSize(32);
        });
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsZeroAndFractionalTokenLifetimes() {
        assertThatThrownBy(() -> new JwtProperties(dummyKey(4), Duration.ZERO, Duration.ofDays(14), "oogachaka"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties(dummyKey(4), Duration.ofMillis(1500), Duration.ofDays(14), "oogachaka"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesConfig {
    }
}
