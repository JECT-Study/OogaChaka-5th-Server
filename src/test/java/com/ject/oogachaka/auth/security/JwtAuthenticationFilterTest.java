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

/** 실제 JWT와 필터를 사용하고 Redis만 목 객체로 대체해 접근 차단과 필터 생략 범위를 검증한다. */
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

    // 서명·만료가 정상이어도 블랙리스트에 있는 access는 다음 필터로 넘기지 않고 AUTH_TOKEN_REVOKED(401)로 차단한다.
    // SecurityContext에 인증 주체가 남지 않는지도 확인한다.
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

    // 공개 URL의 HTTP 메서드를 바꾸거나 하위 경로를 붙인 요청, 보호 API는 JWT 검증을 생략하면 안 된다.
    // 각 요청의 잘못된 인증 헤더가 AUTH_TOKEN_INVALID(401)로 거부되는지 확인한다.
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

    // 서버 경로에 /backend가 붙어도 공개 갱신 API를 인식해 잘못된 access 헤더를 무시해야 한다.
    // 이때 블랙리스트를 조회하거나 인증 주체를 생성하지 않는지 확인한다.
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

    // 블랙리스트 조회가 Redis 장애로 실패하면 access가 정상이어도 요청을 통과시키지 않고 AUTH_STORE_UNAVAILABLE(503)을 반환한다.
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
