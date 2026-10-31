package com.ject.oogachaka.global.infrastructure.client.social;

import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.global.config.OAuthProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class NaverSocialLoginClient extends AbstractSocialLoginClient {

    public NaverSocialLoginClient(OAuthProperties properties, SocialOAuthTransport transport) {
        super(properties, transport);
    }

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.NAVER;
    }

    @Override
    public boolean supportsPkce() {
        return false;
    }

    @Override
    protected SocialUserInfo extractUserInfo(JsonNode response) {
        if (!"00".equals(SocialOAuthTransport.optionalText(response, "resultcode"))) {
            throw SocialOAuthTransport.failed();
        }
        JsonNode profile = response.path("response");
        return new SocialUserInfo(SocialOAuthTransport.requiredText(profile, "id"),
            SocialOAuthTransport.optionalText(profile, "nickname"), SocialOAuthTransport.optionalText(profile, "email"));
    }
}
