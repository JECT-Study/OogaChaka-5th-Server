package com.ject.oogachaka.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ject.oogachaka.auth.enums.AuthProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** 제공자 활성 여부에 따라 필요한 설정을 검증한다. 제공자 콘솔 등록이나 실제 외부 통신 성공은 확인하지 않는다. */
class OAuthPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class)
        .withPropertyValues("app.oauth.state-ttl=5m", "app.oauth.connect-timeout=3s", "app.oauth.read-timeout=5s",
            "app.oauth.providers.google.enabled=false", "app.oauth.providers.google.client-id=",
            "app.oauth.providers.google.client-secret=", "app.oauth.providers.google.redirect-uri=");

    // 사용하지 않는 제공자를 비활성화했으면 client ID·secret·콜백 주소가 비어 있어도 애플리케이션이 시작되어야 한다.
    @Test
    void disabledProviderCanStartWithoutCredentials() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    // 제공자를 활성화했을 때 필수 인증 정보 누락이나 HTTP 외부 API 주소는 시작 단계에서 거부한다.
    // 정상 설정은 client ID와 scope로 바인딩되며 설정 객체의 문자열에 secret이 드러나지 않아야 한다.
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
