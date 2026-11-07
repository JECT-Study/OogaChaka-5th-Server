package com.ject.oogachaka.auth.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** 공개 인증 경로와 HTTP 메서드를 Security 인가와 JWT 필터가 함께 사용한다. */
// [검토] 인가 설정과 JWT 필터가 같은 공개 경로 판단을 공유한다. 한쪽만 공개하면 만료 헤더가 공개 API를 막는 불일치가 생긴다.
public final class AuthRequestMatchers {

    // [검토] 아래 세 경로·메서드 중 하나와 정확히 맞을 때만 공개 인증 요청으로 분류한다.
    public static final RequestMatcher PUBLIC_ENDPOINTS = new OrRequestMatcher(
        // [검토] 인가 URL을 받는 GET 요청은 access 없이 시작할 수 있어야 한다. 다른 HTTP 메서드를 함께 공개하지 않는다.
        PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/api/v1/auth/{provider}/authorize"),
        // [검토] 제공자 인가 코드 제출은 아직 앱 access가 없으므로 공개한다. 본문의 state·코드는 별도 검증한다.
        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/social-login"),
        // [검토] 만료된 access를 대체하는 API이므로 access 필터는 건너뛰고 본문의 refresh만 서비스에서 검증한다.
        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/refresh")
    );

    // [검토] 정적 공통 매처만 필요하므로 이 유틸리티 인스턴스를 생성하지 못하게 한다.
    private AuthRequestMatchers() {
    }
}
