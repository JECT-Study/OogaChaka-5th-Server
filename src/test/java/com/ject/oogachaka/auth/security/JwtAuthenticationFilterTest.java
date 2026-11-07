package com.ject.oogachaka.auth.security;

import static com.ject.oogachaka.global.crypto.TestCryptoKeys.dummyKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.ject.oogachaka.auth.token.JwtTokenProvider;
import com.ject.oogachaka.global.config.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** 실제 JWT 검증기와 필터로 잘못된 인증 헤더의 차단을 검증한다. */
class JwtAuthenticationFilterTest {

    private final JwtTokenProvider jwt = new JwtTokenProvider(
        new JwtProperties(dummyKey(4), Duration.ofMinutes(30), Duration.ofDays(14)),
        Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, new JsonSecurityHandler(JsonMapper.builder().build()));

    // 보호 API에 Bearer 형식이 아닌 Authorization 헤더가 오면 다음 필터로 넘기지 않고 AUTH_TOKEN_INVALID(401)로 거부한다.
    @Test
    void malformedHeaderOnProtectedApiIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test/principal");
        request.addHeader("Authorization", "malformed-header");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (incoming, outgoing) -> {
            throw new AssertionError("잘못된 헤더가 체인을 통과했습니다.");
        });
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_TOKEN_INVALID");
    }
}
