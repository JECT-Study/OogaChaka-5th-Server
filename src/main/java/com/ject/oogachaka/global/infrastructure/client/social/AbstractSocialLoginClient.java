package com.ject.oogachaka.global.infrastructure.client.social;

import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.global.config.OAuthProperties;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;

abstract class AbstractSocialLoginClient implements SocialLoginClient {

    private final OAuthProperties properties;
    private final SocialOAuthTransport transport;

    protected AbstractSocialLoginClient(OAuthProperties properties, SocialOAuthTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    @Override
    public SocialUserInfo getUserInfo(String code, String state, String verifier) {
        OAuthProperties.Provider settings = properties.getEnabledProvider(getProvider());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", settings.getClientId());
        form.add("code", code);
        form.add("redirect_uri", settings.getRedirectUri().toString());
        if (settings.getClientSecret() != null && !settings.getClientSecret().isBlank()) {
            form.add("client_secret", settings.getClientSecret());
        }
        if (getProvider() == AuthProvider.NAVER) {
            form.add("state", state);
        }
        if (supportsPkce()) {
            if (verifier == null || !verifier.matches("[A-Za-z0-9_-]{43}")) {
                throw SocialOAuthTransport.failed();
            }
            form.add("code_verifier", verifier);
        }
        String token = transport.exchangeToken(settings.getTokenUri(), form);
        return extractUserInfo(transport.getUserInfo(settings.getUserInfoUri(), token));
    }

    protected abstract SocialUserInfo extractUserInfo(JsonNode response);
}
