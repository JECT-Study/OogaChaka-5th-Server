package com.ject.oogachaka.auth.service;

import com.ject.oogachaka.auth.dto.AuthorizationResponse;
import com.ject.oogachaka.auth.dto.SocialLoginRequest;
import com.ject.oogachaka.auth.dto.SocialLoginResponse;
import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.auth.repository.OAuthStateStore;
import com.ject.oogachaka.global.config.OAuthProperties;
import com.ject.oogachaka.global.crypto.HmacPurpose;
import com.ject.oogachaka.global.crypto.HmacService;
import com.ject.oogachaka.global.infrastructure.client.social.SocialLoginClient;
import com.ject.oogachaka.global.infrastructure.client.social.SocialUserInfo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 외부 통신, DB 트랜잭션, Redis 발급을 조정하며 외부 호출 중 DB 커넥션을 점유하지 않는다.
 * 서비스 전체를 DB 트랜잭션으로 감싸 외부 통신 대기까지 포함하지 않으려는 분리다. DB 가입 후 토큰 저장 실패 시 가입 기록은 남는다.
 */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final OAuthProperties properties;
    private final OAuthStateStore stateStore;
    private final HmacService hmacService;
    private final SocialAccountService accounts;
    private final TokenService tokens;
    private final SocialLoginClient client;
    private final SecureRandom random = new SecureRandom();

    // 선택한 제공자의 로그인 시작 URL을 구성한다.
    public AuthorizationResponse authorize(AuthProvider provider) {
        OAuthProperties.Provider settings = properties.getProvider(provider);
        boolean pkce = SocialLoginClient.supportsPkce(provider);
        String state = randomValue();
        String verifier = pkce ? randomValue() : "";
        stateStore.save(state, verifier);

        UriComponentsBuilder uri = UriComponentsBuilder.fromUri(settings.getAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", "{clientId}")
                .queryParam("redirect_uri", "{redirectUri}")
                .queryParam("state", state);

        if (!settings.getScopes().isEmpty()) {
            uri.queryParam("scope", "{scope}");
        }

        if (pkce) {
            uri.queryParam("code_challenge", challenge(verifier))
                    .queryParam("code_challenge_method", "S256");
        }

        String scope = String.join(provider == AuthProvider.KAKAO ? "," : " ", settings.getScopes());
        String url = uri.encode().buildAndExpand(Map.of(
                "clientId", settings.getClientId(),
                "redirectUri", settings.getRedirectUri().toString(),
                "scope", scope)).toUriString();
        return new AuthorizationResponse(url, state);
    }

    public SocialLoginResponse login(SocialLoginRequest request) {
        String verifier = stateStore.consume(request.state());
        SocialUserInfo info = client.getUserInfo(request.provider(), request.code(), request.state(), verifier);
        String hash = hmacService.hash(info.getProviderUserId(), HmacPurpose.SOCIAL_ID);
        SocialAccountResult result = accounts.findOrCreate(request.provider(), hash, info.getNickname(), info.getEmail());
        return new SocialLoginResponse(result.userId(), result.isNewUser(), tokens.issue(result.userId()));
    }

    // state와 PKCE verifier에 사용하는 URL 안전 난수 생성 규칙을 한 곳에 둔다.
    private String randomValue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String challenge(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.");
        }
    }
}
