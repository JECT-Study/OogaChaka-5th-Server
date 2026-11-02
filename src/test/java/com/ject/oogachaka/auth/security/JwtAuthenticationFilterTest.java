package com.ject.oogachaka.auth.security;

import static com.ject.oogachaka.global.crypto.TestCryptoKeys.dummyKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.auth.repository.AuthTokenStore;
import com.ject.oogachaka.auth.token.JwtTokenProvider;
import com.ject.oogachaka.auth.token.TokenPair;
import com.ject.oogachaka.global.config.JwtProperties;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

class JwtAuthenticationFilterTest {

    private final JwtProperties properties = new JwtProperties(dummyKey(4), Duration.ofMinutes(30), Duration.ofDays(14), "oogachaka");
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
    private final JwtTokenProvider jwt = new JwtTokenProvider(properties, clock);
    private final AuthTokenStore store = mock(AuthTokenStore.class);
    private final JsonSecurityHandler handler = new JsonSecurityHandler(JsonMapper.builder().build());
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, store, handler);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blacklistPreventsReusingLoggedOutAccess() throws Exception {
        TokenPair pair = jwt.issuePair(42L);
        when(store.isBlacklisted(pair.access().claims())).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(pair.access().value()), response, (incoming, outgoing) -> {
            throw new AssertionError("블랙리스트 토큰이 체인을 통과했습니다.");
        });
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_TOKEN_REVOKED", "\"data\":null");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/v1/auth/refresh", "POST,/api/v1/auth/google/authorize",
        "POST,/api/v1/auth/logout", "POST,/api/v1/auth/refresh/extra", "POST,/api/test/principal"})
    void onlyExactPublicMethodAndPathPairsSkipValidation(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "malformed-header");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (incoming, outgoing) -> {
            throw new AssertionError("보호 경로의 JWT 검증을 건너뛰었습니다.");
        });
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_TOKEN_INVALID");
    }

    @Test
    void publicMatcherHandlesContextPathWithoutAuthenticatingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/backend/api/v1/auth/refresh");
        request.setContextPath("/backend");
        request.addHeader("Authorization", "malformed-header");
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (incoming, outgoing) -> called.set(true));
        assertThat(called).isTrue();
        org.mockito.Mockito.verifyNoInteractions(store);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void redisFailureDoesNotAuthenticate() throws Exception {
        TokenPair pair = jwt.issuePair(42L);
        when(store.isBlacklisted(pair.access().claims())).thenThrow(new BusinessException(ErrorCode.AUTH_STORE_UNAVAILABLE));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(pair.access().value()), response, (incoming, outgoing) -> {
            throw new AssertionError("Redis 장애 시 인증을 허용했습니다.");
        });
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("AUTH_STORE_UNAVAILABLE");
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
