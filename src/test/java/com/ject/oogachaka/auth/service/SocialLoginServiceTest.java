package com.ject.oogachaka.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.auth.dto.AuthorizationResponse;
import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.auth.repository.OAuthStateStore;
import com.ject.oogachaka.global.config.OAuthProperties;
import com.ject.oogachaka.global.crypto.HmacService;
import com.ject.oogachaka.global.infrastructure.client.social.SocialLoginClient;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 외부 제공자·DB·Redis를 목 객체로 대체해 인가 URL 생성 규칙을 검증한다. */
class SocialLoginServiceTest {

    private final OAuthProperties properties = mock(OAuthProperties.class);
    private final OAuthStateStore states = mock(OAuthStateStore.class);
    private final SocialLoginService service = new SocialLoginService(properties, states, mock(HmacService.class),
        mock(SocialAccountService.class), mock(TokenService.class), mock(SocialLoginClient.class));

    // 로그인 시도별 검증에 쓸 verifier 원문은 서버 저장소에만 전달하고, 인가 URL에는 state와 S256 해시를 넣어야 한다.
    // 콜백 URL·scope의 인코딩이 유지되고 client secret과 verifier 원문이 인가 URL에 노출되지 않는지 확인한다.
    @Test
    void authorizationIncludesS256ChallengeAndKeepsVerifierServerSide() throws Exception {
        when(properties.getProvider(AuthProvider.GOOGLE)).thenReturn(new OAuthProperties.Provider(
            "TEST_ONLY_CLIENT", "TEST_ONLY_SECRET", URI.create("https://app.example/callback?next=/home"),
            URI.create("https://accounts.google.com/o/oauth2/v2/auth"), URI.create("https://oauth2.googleapis.com/token"),
            URI.create("https://openidconnect.googleapis.com/v1/userinfo"), List.of("openid", "profile", "email")));

        AuthorizationResponse response = service.authorize(AuthProvider.GOOGLE);
        ArgumentCaptor<String> captured = ArgumentCaptor.forClass(String.class);
        verify(states).save(eq(response.state()), captured.capture());
        String verifier = captured.getValue();
        String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));

        assertThat(response.state()).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(verifier);
        assertThat(response.authorizationUrl()).contains("code_challenge=" + expected, "code_challenge_method=S256")
            .doesNotContain(verifier, "TEST_ONLY_SECRET");
        String query = URLDecoder.decode(URI.create(response.authorizationUrl()).getRawQuery(), StandardCharsets.UTF_8);
        assertThat(query).contains("redirect_uri=https://app.example/callback?next=/home", "scope=openid profile email");
    }
}
