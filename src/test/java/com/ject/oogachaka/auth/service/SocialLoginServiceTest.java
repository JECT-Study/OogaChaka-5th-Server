package com.ject.oogachaka.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.auth.dto.AuthorizationResponse;
import com.ject.oogachaka.auth.dto.OAuthState;
import com.ject.oogachaka.auth.dto.SocialLoginRequest;
import com.ject.oogachaka.auth.dto.TokenResponse;
import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.auth.repository.OAuthStateStore;
import com.ject.oogachaka.global.config.OAuthProperties;
import com.ject.oogachaka.global.crypto.HmacPurpose;
import com.ject.oogachaka.global.crypto.HmacService;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.infrastructure.client.social.SocialLoginClient;
import com.ject.oogachaka.global.infrastructure.client.social.SocialUserInfo;
import com.ject.oogachaka.global.response.code.ErrorCode;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class SocialLoginServiceTest {

    private static final String STATE = "s".repeat(43);
    private static final String VERIFIER = "v".repeat(43);
    private static final String HASH = "a".repeat(64);
    private final OAuthProperties properties = mock(OAuthProperties.class);
    private final OAuthStateStore states = mock(OAuthStateStore.class);
    private final HmacService hmac = mock(HmacService.class);
    private final SocialAccountService accounts = mock(SocialAccountService.class);
    private final TokenService tokens = mock(TokenService.class);
    private final SocialLoginClient client = mock(SocialLoginClient.class);
    private SocialLoginService service;

    @BeforeEach
    void setUp() {
        when(client.getProvider()).thenReturn(AuthProvider.GOOGLE);
        when(client.supportsPkce()).thenReturn(true);
        when(properties.getEnabledProvider(AuthProvider.GOOGLE)).thenReturn(new OAuthProperties.Provider(true,
            "TEST_ONLY_CLIENT", "TEST_ONLY_SECRET", URI.create("https://app.example/callback?next=/home"),
            URI.create("https://accounts.google.com/o/oauth2/v2/auth"), URI.create("https://oauth2.googleapis.com/token"),
            URI.create("https://openidconnect.googleapis.com/v1/userinfo"), List.of("openid", "profile", "email")));
        service = new SocialLoginService(properties, states, hmac, accounts, tokens, List.of(client));
    }

    @Test
    void authorizationUsesRandomStateAndServerSideS256Verifier() throws Exception {
        when(states.save(anyString(), any())).thenReturn(true);
        AuthorizationResponse response = service.authorize(AuthProvider.GOOGLE);
        ArgumentCaptor<OAuthState> captured = ArgumentCaptor.forClass(OAuthState.class);
        verify(states).save(eq(response.state()), captured.capture());
        String verifier = captured.getValue().verifier();
        String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));

        assertThat(response.state()).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(verifier);
        assertThat(response.authorizationUrl()).contains("code_challenge=" + expected, "code_challenge_method=S256")
            .doesNotContain(verifier, "TEST_ONLY_SECRET");
        String query = URLDecoder.decode(URI.create(response.authorizationUrl()).getRawQuery(), StandardCharsets.UTF_8);
        assertThat(query).contains("redirect_uri=https://app.example/callback?next=/home", "scope=openid profile email");
    }

    @Test
    void newUserUsesSocialHmacAndReturnsRegistrationFlag() {
        prepareLogin();
        when(accounts.findOrCreate(AuthProvider.GOOGLE, HASH, "별명", "test@example.invalid"))
            .thenReturn(new SocialAccountResult(42L, true));
        var response = service.login(request());
        assertThat(response.isNewUser()).isTrue();
        assertThat(response.userId()).isEqualTo(42L);
        verify(hmac).hash("TEST_ONLY_RAW_ID", HmacPurpose.SOCIAL_ID);
        verify(tokens).issue(42L);
    }

    @Test
    void existingUserDoesNotReportNewRegistration() {
        prepareLogin();
        when(accounts.findOrCreate(AuthProvider.GOOGLE, HASH, "별명", "test@example.invalid"))
            .thenReturn(new SocialAccountResult(42L, false));
        assertThat(service.login(request()).isNewUser()).isFalse();
    }

    @Test
    void registrationRaceQueriesWinnerAfterFailedTransaction() {
        prepareLogin();
        when(accounts.findOrCreate(AuthProvider.GOOGLE, HASH, "별명", "test@example.invalid"))
            .thenThrow(new DataIntegrityViolationException("TEST_ONLY_UNIQUE"));
        when(accounts.getExisting(AuthProvider.GOOGLE, HASH)).thenReturn(new SocialAccountResult(42L, false));
        assertThat(service.login(request()).isNewUser()).isFalse();
        verify(accounts).getExisting(AuthProvider.GOOGLE, HASH);
        verify(tokens).issue(42L);
    }

    @Test
    void invalidStateStopsBeforeProviderDatabaseOrTokens() {
        when(states.consume(STATE, AuthProvider.GOOGLE)).thenThrow(new BusinessException(ErrorCode.AUTH_STATE_INVALID));
        assertThatThrownBy(() -> service.login(request())).isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_STATE_INVALID));
        verify(client, org.mockito.Mockito.never()).getUserInfo(any(), any(), any());
        verifyNoInteractions(accounts, hmac, tokens);
    }

    private void prepareLogin() {
        when(states.consume(STATE, AuthProvider.GOOGLE)).thenReturn(new OAuthState(AuthProvider.GOOGLE, VERIFIER));
        when(client.getUserInfo("TEST_ONLY_CODE", STATE, VERIFIER))
            .thenReturn(new SocialUserInfo("TEST_ONLY_RAW_ID", "별명", "test@example.invalid"));
        when(hmac.hash("TEST_ONLY_RAW_ID", HmacPurpose.SOCIAL_ID)).thenReturn(HASH);
        when(tokens.issue(42L)).thenReturn(new TokenResponse("Bearer", "TEST_ONLY_ACCESS", "TEST_ONLY_REFRESH", 1800, 1209600));
    }

    private SocialLoginRequest request() {
        return new SocialLoginRequest(AuthProvider.GOOGLE, "TEST_ONLY_CODE", STATE);
    }
}
