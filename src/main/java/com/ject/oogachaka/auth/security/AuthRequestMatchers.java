package com.ject.oogachaka.auth.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** 공개 인증 경로와 HTTP 메서드를 Security 인가와 JWT 필터가 함께 사용한다. */
public final class AuthRequestMatchers {

    public static final RequestMatcher PUBLIC_ENDPOINTS = new OrRequestMatcher(
        PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/api/v1/auth/{provider}/authorize"),
        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/social-login"),
        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/refresh")
    );

    private AuthRequestMatchers() {
    }
}
