package com.ject.oogachaka.global.config;

import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 활성 제공자의 설정만 검증한다. secret이 출력되지 않도록 toString을 생성하지 않는다. */
@Getter
@ConfigurationProperties(prefix = "app.oauth")
public class OAuthProperties {

    private final Duration stateTtl;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Map<AuthProvider, Provider> providers;

    public OAuthProperties(Duration stateTtl, Duration connectTimeout, Duration readTimeout,
        Map<AuthProvider, Provider> providers) {
        validateDuration(stateTtl);
        validateDuration(connectTimeout);
        validateDuration(readTimeout);
        this.stateTtl = stateTtl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.providers = providers == null ? Map.of() : Map.copyOf(providers);
        this.providers.forEach((name, provider) -> {
            if (provider.isEnabled()) {
                provider.validate(name);
            }
        });
    }

    public Provider getEnabledProvider(AuthProvider provider) {
        Provider settings = providers.get(provider);
        if (settings == null || !settings.isEnabled()) {
            throw new BusinessException(ErrorCode.AUTH_PROVIDER_DISABLED);
        }
        return settings;
    }

    private static void validateDuration(Duration value) {
        if (value == null || value.isNegative() || value.isZero() || value.toMillis() < 1) {
            throw new IllegalStateException("OAuth TTL과 타임아웃은 양수여야 합니다.");
        }
    }

    @Getter
    public static class Provider {
        private final boolean enabled;
        private final String clientId;
        private final String clientSecret;
        private final URI redirectUri;
        private final URI authorizationUri;
        private final URI tokenUri;
        private final URI userInfoUri;
        private final List<String> scopes;

        public Provider(boolean enabled, String clientId, String clientSecret, URI redirectUri,
            URI authorizationUri, URI tokenUri, URI userInfoUri, List<String> scopes) {
            this.enabled = enabled;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.redirectUri = redirectUri;
            this.authorizationUri = authorizationUri;
            this.tokenUri = tokenUri;
            this.userInfoUri = userInfoUri;
            this.scopes = scopes == null ? List.of() : List.copyOf(scopes);
        }

        private void validate(AuthProvider provider) {
            if (clientId == null || clientId.isBlank()
                || (provider != AuthProvider.KAKAO && (clientSecret == null || clientSecret.isBlank()))) {
                throw new IllegalStateException("활성 OAuth 제공자의 client-id와 client-secret이 필요합니다.");
            }
            if (redirectUri == null || !redirectUri.isAbsolute() || redirectUri.getFragment() != null) {
                throw new IllegalStateException("활성 OAuth 제공자의 절대 redirect-uri가 필요합니다.");
            }
            for (URI endpoint : new URI[]{authorizationUri, tokenUri, userInfoUri}) {
                if (endpoint == null || !"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null
                    || endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
                    throw new IllegalStateException("OAuth 외부 API 주소는 HTTPS 절대 URI여야 합니다.");
                }
            }
        }
    }
}
