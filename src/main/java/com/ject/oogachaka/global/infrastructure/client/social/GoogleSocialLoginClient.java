package com.ject.oogachaka.global.infrastructure.client.social;

import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.global.config.OAuthProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class GoogleSocialLoginClient extends AbstractSocialLoginClient {

    public GoogleSocialLoginClient(OAuthProperties properties, SocialOAuthTransport transport) {
        super(properties, transport);
    }

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public boolean supportsPkce() {
        return true;
    }

    @Override
    protected SocialUserInfo extractUserInfo(JsonNode response) {
        return new SocialUserInfo(SocialOAuthTransport.requiredText(response, "sub"),
            SocialOAuthTransport.optionalText(response, "name"), SocialOAuthTransport.optionalText(response, "email"));
    }
}
