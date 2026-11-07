package com.ject.oogachaka.global.infrastructure.client.social;

import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.global.config.OAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;

/** 인가 코드를 제공자 토큰으로 교환하고 프로필을 조회해 공통 사용자 정보로 바꾼다. */
@Component
@RequiredArgsConstructor
public class SocialLoginClient {

    private final OAuthProperties properties;
    private final SocialOAuthTransport transport;

    // 네이버는 기존 OAuth API에 PKCE가 문서화되어 있지 않아 구글·카카오만 PKCE를 쓴다.
    public static boolean supportsPkce(AuthProvider provider) {
        return provider != AuthProvider.NAVER;
    }

    public SocialUserInfo getUserInfo(AuthProvider provider, String code, String state, String verifier) {
        OAuthProperties.Provider settings = properties.getProvider(provider);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", settings.getClientId());
        form.add("code", code);
        form.add("redirect_uri", settings.getRedirectUri().toString());
        // 카카오는 콘솔에서 client secret을 끈 경우 빈 값이다.
        if (settings.getClientSecret() != null && !settings.getClientSecret().isBlank()) {
            form.add("client_secret", settings.getClientSecret());
        }
        // 네이버는 토큰 요청에 state가 필수다.
        if (provider == AuthProvider.NAVER) {
            form.add("state", state);
        }
        if (supportsPkce(provider)) {
            form.add("code_verifier", verifier);
        }

        String token = transport.exchangeToken(settings.getTokenUri(), form);
        JsonNode profile = transport.getUserInfo(settings.getUserInfoUri(), token);
        return switch (provider) {
            case GOOGLE -> google(profile);
            case KAKAO -> kakao(profile);
            case NAVER -> naver(profile);
        };
    }

    private SocialUserInfo google(JsonNode response) {
        // 구글 userinfo의 sub를 안정적인 소셜 계정 ID로 사용한다. 이메일을 계정 식별자로 대체하지 않는다.
        return new SocialUserInfo(SocialOAuthTransport.requiredText(response, "sub"),
            SocialOAuthTransport.optionalText(response, "name"), SocialOAuthTransport.optionalText(response, "email"));
    }

    private SocialUserInfo kakao(JsonNode response) {
        // 카카오 사용자 정보 API의 최상위 숫자 id를 읽는다. OpenID Connect의 sub 형식과 다르다.
        JsonNode id = response.path("id");
        // id가 없으면 asLong()이 0을 돌려줘 모든 사용자가 0번 계정으로 합쳐질 수 있으므로 거부한다.
        if (!id.isIntegralNumber() || id.asLong(0) <= 0) {
            throw SocialOAuthTransport.failed();
        }
        // 동의받은 프로필·이메일이 들어 있는 하위 객체를 읽는다. path는 없는 필드를 결측 노드로 표현한다.
        JsonNode account = response.path("kakao_account");
        return new SocialUserInfo(Long.toString(id.asLong()),
            SocialOAuthTransport.optionalText(account.path("profile"), "nickname"),
            SocialOAuthTransport.optionalText(account, "email"));
    }

    private SocialUserInfo naver(JsonNode response) {
        // 네이버는 사용자 필드를 최상위가 아닌 response 객체 안에 제공하므로 그 객체를 읽는다.
        JsonNode profile = response.path("response");
        return new SocialUserInfo(SocialOAuthTransport.requiredText(profile, "id"),
            SocialOAuthTransport.optionalText(profile, "nickname"), SocialOAuthTransport.optionalText(profile, "email"));
    }
}
