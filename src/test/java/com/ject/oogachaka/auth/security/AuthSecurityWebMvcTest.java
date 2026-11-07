package com.ject.oogachaka.auth.security;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ject.oogachaka.auth.controller.AuthController;
import com.ject.oogachaka.auth.dto.AuthorizationResponse;
import com.ject.oogachaka.auth.dto.TokenResponse;
import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.auth.enums.TokenType;
import com.ject.oogachaka.auth.service.SocialLoginService;
import com.ject.oogachaka.auth.service.TokenService;
import com.ject.oogachaka.auth.token.JwtClaims;
import com.ject.oogachaka.auth.token.JwtTokenProvider;
import com.ject.oogachaka.global.config.CorsProperties;
import com.ject.oogachaka.global.config.SecurityConfig;
import com.ject.oogachaka.global.exception.GlobalExceptionHandler;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.ApiResponse;
import com.ject.oogachaka.global.response.code.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 실제 MVC·Security 설정으로 API의 공개 범위, 인증 응답과 CORS를 검증한다. JWT 검증과 업무 서비스는 목 객체로 대체한다. */
@WebMvcTest(controllers = {AuthController.class, AuthSecurityWebMvcTest.ProbeController.class},
    properties = {"app.cors.allowed-origins=https://app.example"})
@Import({SecurityConfig.class, JsonSecurityHandler.class, GlobalExceptionHandler.class,
    AuthSecurityWebMvcTest.PropertiesConfig.class, AuthController.class, AuthSecurityWebMvcTest.ProbeController.class})
class AuthSecurityWebMvcTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private JwtTokenProvider jwt;
    @MockitoBean
    private SocialLoginService socialLogin;
    @MockitoBean
    private TokenService tokens;

    // 인증 헤더 없이 인가 URL·소셜 로그인·갱신 API는 호출할 수 있어야 한다.
    // 로그아웃과 일반 보호 API는 같은 조건에서 UNAUTHORIZED(401)를 반환해야 한다.
    @Test
    void authorizeAndRefreshArePublicButLogoutAndOtherApiRequireAuthentication() throws Exception {
        when(socialLogin.authorize(AuthProvider.GOOGLE)).thenReturn(new AuthorizationResponse("https://provider.example", "s".repeat(43)));
        mvc.perform(get("/api/v1/auth/google/authorize")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"));
        when(tokens.refresh("TEST_ONLY_REFRESH")).thenReturn(new TokenResponse("Bearer", "TEST_ONLY_ACCESS", "TEST_ONLY_NEW", 1800, 1209600));
        mvc.perform(post("/api/v1/auth/social-login").contentType("application/json")
                .content("{\"provider\":\"GOOGLE\",\"code\":\"TEST_ONLY_CODE\",\"state\":\"" + "s".repeat(43) + "\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content("{\"refreshToken\":\"TEST_ONLY_REFRESH\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout").contentType("application/json").content("{\"refreshToken\":\"TEST_ONLY_REFRESH\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/test/principal")).andExpect(status().isUnauthorized());
    }

    // 만료된 access 또는 잘못된 Authorization 헤더가 붙어도 공개 인증 API는 정상 처리되어야 한다.
    // 불필요한 JWT 검증 자체가 호출되지 않는지도 확인한다.
    @ParameterizedTest
    @ValueSource(strings = {"Bearer TEST_ONLY_EXPIRED", "malformed-header"})
    void publicAuthenticationApisIgnoreAccessHeaders(String authorization) throws Exception {
        when(jwt.validate("TEST_ONLY_EXPIRED", TokenType.ACCESS)).thenThrow(new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED));
        when(socialLogin.authorize(AuthProvider.GOOGLE)).thenReturn(new AuthorizationResponse("https://provider.example", "s".repeat(43)));
        when(tokens.refresh("TEST_ONLY_REFRESH")).thenReturn(new TokenResponse("Bearer", "TEST_ONLY_ACCESS", "TEST_ONLY_NEW", 1800, 1209600));

        mvc.perform(get("/api/v1/auth/google/authorize").header("Authorization", authorization))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/social-login").header("Authorization", authorization).contentType("application/json")
                .content("{\"provider\":\"GOOGLE\",\"code\":\"TEST_ONLY_CODE\",\"state\":\"" + "s".repeat(43) + "\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").header("Authorization", authorization).contentType("application/json")
                .content("{\"refreshToken\":\"TEST_ONLY_REFRESH\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.refreshToken").value("TEST_ONLY_NEW"));
        verifyNoInteractions(jwt);
    }

    // 인증된 요청의 Long 사용자 ID가 컨트롤러와 로그아웃에 전달되어야 한다.
    // 컨트롤러가 권한 거부 예외를 던지면 공통 응답의 FORBIDDEN(403)으로 처리되는지도 확인한다.
    @Test
    void longPrincipalAndDeniedResponseFollowContract() throws Exception {
        when(jwt.validate("TEST_ONLY_ACCESS", TokenType.ACCESS)).thenReturn(new JwtClaims(42L,
            "TEST_ONLY_JTI", TokenType.ACCESS, Instant.now(), Instant.now().plusSeconds(1800)));
        mvc.perform(get("/api/test/principal").header("Authorization", "Bearer TEST_ONLY_ACCESS"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(42));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer TEST_ONLY_ACCESS")
                .contentType("application/json").content("{\"refreshToken\":\"TEST_ONLY_REFRESH\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"));
        org.mockito.Mockito.verify(tokens).logout(42L, "TEST_ONLY_REFRESH");
        mvc.perform(get("/api/test/denied").header("Authorization", "Bearer TEST_ONLY_ACCESS"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // 허용된 웹 출처의 로그아웃 사전 요청은 JWT 없이 성공하고 해당 출처를 CORS 응답 헤더에 포함해야 한다.
    @Test
    void allowedCorsPreflightDoesNotNeedJwt() throws Exception {
        mvc.perform(options("/api/v1/auth/logout").header("Origin", "https://app.example")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "authorization,content-type"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://app.example"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CorsProperties.class)
    static class PropertiesConfig {
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/test/principal")
        ApiResponse<Long> principal(@AuthenticationPrincipal Long userId) {
            return ApiResponse.success(userId);
        }

        @GetMapping("/api/test/denied")
        ApiResponse<Void> denied() {
            throw new AccessDeniedException("TEST_ONLY_DENIED");
        }
    }
}
