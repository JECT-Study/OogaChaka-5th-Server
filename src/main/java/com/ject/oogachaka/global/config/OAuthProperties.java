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

/** 소셜 로그인 설정. client-id가 비어 있는 제공자는 사용하지 않는 것으로 본다. secret이 출력되지 않도록 toString을 생성하지 않는다. */
@Getter
@ConfigurationProperties(prefix = "app.oauth")
public class OAuthProperties {

    private final Duration stateTtl;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Map<AuthProvider, Provider> providers;

    public OAuthProperties(Duration stateTtl, Duration connectTimeout, Duration readTimeout, Map<AuthProvider, Provider> providers) {
        this.stateTtl = stateTtl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.providers = providers == null ? Map.of() : Map.copyOf(providers);
    }

    public Provider getProvider(AuthProvider provider) {
        Provider settings = providers.get(provider);
        if (settings == null || settings.getClientId() == null || settings.getClientId().isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_PROVIDER_UNSUPPORTED);
        }
        return settings;
    }

    @Getter
    public static class Provider {
        private final String clientId;
        private final String clientSecret;
        private final URI redirectUri;
        private final URI authorizationUri;
        private final URI tokenUri;
        private final URI userInfoUri;
        private final List<String> scopes;

        public Provider(String clientId, String clientSecret, URI redirectUri,
            URI authorizationUri, URI tokenUri, URI userInfoUri, List<String> scopes) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.redirectUri = redirectUri;
            this.authorizationUri = authorizationUri;
            this.tokenUri = tokenUri;
            this.userInfoUri = userInfoUri;
            this.scopes = scopes == null ? List.of() : List.copyOf(scopes);
        }
    }
}
