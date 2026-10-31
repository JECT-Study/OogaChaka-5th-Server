package com.ject.oogachaka.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ject.oogachaka.auth.enums.AuthProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OAuthPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class)
        .withPropertyValues("app.oauth.state-ttl=5m", "app.oauth.connect-timeout=3s", "app.oauth.read-timeout=5s",
            "app.oauth.providers.google.enabled=false", "app.oauth.providers.google.client-id=",
            "app.oauth.providers.google.client-secret=", "app.oauth.providers.google.redirect-uri=");

    @Test
    void disabledProviderCanStartWithoutCredentials() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void enabledProviderRequiresSecretAndHttpsEndpoints() {
        runner.withPropertyValues("app.oauth.providers.google.enabled=true").run(context -> assertThat(context).hasFailed());
        validProvider().withPropertyValues("app.oauth.providers.google.token-uri=http://provider.example/token")
            .run(context -> assertThat(context).hasFailed());
        validProvider().run(context -> {
            assertThat(context).hasNotFailed();
            OAuthProperties.Provider provider = context.getBean(OAuthProperties.class).getEnabledProvider(AuthProvider.GOOGLE);
            assertThat(provider.getClientId()).isEqualTo("TEST_ONLY_CLIENT");
            assertThat(provider.getScopes()).containsExactly("openid", "profile", "email");
            assertThat(provider.toString()).doesNotContain("TEST_ONLY_SECRET");
        });
    }

    private ApplicationContextRunner validProvider() {
        return runner.withPropertyValues("app.oauth.providers.google.enabled=true",
            "app.oauth.providers.google.client-id=TEST_ONLY_CLIENT", "app.oauth.providers.google.client-secret=TEST_ONLY_SECRET",
            "app.oauth.providers.google.redirect-uri=https://app.example/callback",
            "app.oauth.providers.google.authorization-uri=https://provider.example/authorize",
            "app.oauth.providers.google.token-uri=https://provider.example/token",
            "app.oauth.providers.google.user-info-uri=https://provider.example/userinfo",
            "app.oauth.providers.google.scopes=openid,profile,email");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OAuthProperties.class)
    static class PropertiesConfig {
    }
}
