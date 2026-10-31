package com.ject.oogachaka.auth.service;

import com.ject.oogachaka.auth.dto.AuthorizationResponse;
import com.ject.oogachaka.auth.dto.OAuthState;
import com.ject.oogachaka.auth.dto.SocialLoginRequest;
import com.ject.oogachaka.auth.dto.SocialLoginResponse;
import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.auth.repository.OAuthStateStore;
import com.ject.oogachaka.global.config.OAuthProperties;
import com.ject.oogachaka.global.crypto.HmacPurpose;
import com.ject.oogachaka.global.crypto.HmacService;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.infrastructure.client.social.SocialLoginClient;
import com.ject.oogachaka.global.infrastructure.client.social.SocialUserInfo;
import com.ject.oogachaka.global.response.code.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/** 외부 통신, DB 트랜잭션, Redis 발급을 조정하며 외부 호출 중 DB 커넥션을 점유하지 않는다. */
@Service
public class SocialLoginService {

    private final OAuthProperties properties;
    private final OAuthStateStore stateStore;
    private final HmacService hmacService;
    private final SocialAccountService accounts;
    private final TokenService tokens;
    private final Map<AuthProvider, SocialLoginClient> clients;
    private final SecureRandom random = new SecureRandom();

    public SocialLoginService(OAuthProperties properties, OAuthStateStore stateStore, HmacService hmacService,
        SocialAccountService accounts, TokenService tokens, List<SocialLoginClient> clients) {
        this.properties = properties;
        this.stateStore = stateStore;
        this.hmacService = hmacService;
        this.accounts = accounts;
        this.tokens = tokens;
        Map<AuthProvider, SocialLoginClient> strategies = new EnumMap<>(AuthProvider.class);
        for (SocialLoginClient client : clients) {
            if (strategies.put(client.getProvider(), client) != null) {
                throw new IllegalStateException("소셜 제공자 클라이언트가 중복 등록되었습니다.");
            }
        }
        this.clients = Map.copyOf(strategies);
    }

    public AuthorizationResponse authorize(AuthProvider provider) {
        OAuthProperties.Provider settings = properties.getEnabledProvider(provider);
        SocialLoginClient client = getClient(provider);
        String state = randomValue();
        String verifier = client.supportsPkce() ? randomValue() : "";
        if (!stateStore.save(state, new OAuthState(provider, verifier))) {
            throw new BusinessException(ErrorCode.AUTH_STORE_UNAVAILABLE);
        }
        UriComponentsBuilder uri = UriComponentsBuilder.fromUri(settings.getAuthorizationUri())
            .queryParam("response_type", "code").queryParam("client_id", "{clientId}")
            .queryParam("redirect_uri", "{redirectUri}").queryParam("state", state);
        if (!settings.getScopes().isEmpty()) {
            uri.queryParam("scope", "{scope}");
        }
        if (client.supportsPkce()) {
            uri.queryParam("code_challenge", challenge(verifier)).queryParam("code_challenge_method", "S256");
        }
        String scope = String.join(provider == AuthProvider.KAKAO ? "," : " ", settings.getScopes());
        String url = uri.encode().buildAndExpand(Map.of("clientId", settings.getClientId(),
            "redirectUri", settings.getRedirectUri().toString(), "scope", scope)).toUriString();
        return new AuthorizationResponse(url, state);
    }

    public SocialLoginResponse login(SocialLoginRequest request) {
        properties.getEnabledProvider(request.provider());
        OAuthState state = stateStore.consume(request.state(), request.provider());
        SocialUserInfo info = getClient(request.provider()).getUserInfo(request.code(), request.state(), state.verifier());
        String hash = hmacService.hash(info.getProviderUserId(), HmacPurpose.SOCIAL_ID);
        SocialAccountResult result;
        try {
            result = accounts.findOrCreate(request.provider(), hash, info.getNickname(), info.getEmail());
        } catch (DataIntegrityViolationException exception) {
            // 별도 트랜잭션에서 실패한 사용자/소셜 계정이 모두 롤백된 뒤 승자의 계정을 조회한다.
            result = accounts.getExisting(request.provider(), hash);
        }
        return new SocialLoginResponse(result.userId(), result.isNewUser(), tokens.issue(result.userId()));
    }

    private SocialLoginClient getClient(AuthProvider provider) {
        SocialLoginClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException(ErrorCode.AUTH_PROVIDER_UNSUPPORTED);
        }
        return client;
    }

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
