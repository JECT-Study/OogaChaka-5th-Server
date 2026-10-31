package com.ject.oogachaka.global.infrastructure.client.social;

import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.global.config.OAuthProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class KakaoSocialLoginClient extends AbstractSocialLoginClient {

    public KakaoSocialLoginClient(OAuthProperties properties, SocialOAuthTransport transport) {
        super(properties, transport);
    }

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public boolean supportsPkce() {
        return true;
    }

    @Override
    protected SocialUserInfo extractUserInfo(JsonNode response) {
        JsonNode id = response.path("id");
        if (!id.isIntegralNumber() || id.asLong(0) <= 0) {
            throw SocialOAuthTransport.failed();
        }
        JsonNode account = response.path("kakao_account");
        return new SocialUserInfo(Long.toString(id.asLong()),
            SocialOAuthTransport.optionalText(account.path("profile"), "nickname"),
            SocialOAuthTransport.optionalText(account, "email"));
    }
}
