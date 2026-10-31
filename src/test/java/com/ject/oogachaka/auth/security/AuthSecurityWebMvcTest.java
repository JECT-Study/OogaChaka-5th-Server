package com.ject.oogachaka.auth.security;

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
import com.ject.oogachaka.auth.repository.AuthTokenStore;
import com.ject.oogachaka.auth.service.SocialLoginService;
import com.ject.oogachaka.auth.service.TokenService;
import com.ject.oogachaka.auth.token.JwtClaims;
import com.ject.oogachaka.auth.token.JwtTokenProvider;
import com.ject.oogachaka.global.config.CorsProperties;
import com.ject.oogachaka.global.config.SecurityConfig;
import com.ject.oogachaka.global.exception.GlobalExceptionHandler;
import com.ject.oogachaka.global.response.ApiResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(controllers = {AuthController.class, AuthSecurityWebMvcTest.ProbeController.class},
    properties = {"app.cors.allowed-origins=https://app.example", "app.cors.allow-credentials=true"})
@Import({SecurityConfig.class, JsonSecurityHandler.class, GlobalExceptionHandler.class,
    AuthSecurityWebMvcTest.PropertiesConfig.class, AuthController.class, AuthSecurityWebMvcTest.ProbeController.class})
class AuthSecurityWebMvcTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private JwtTokenProvider jwt;
    @MockitoBean
    private AuthTokenStore store;
    @MockitoBean
    private SocialLoginService socialLogin;
    @MockitoBean
    private TokenService tokens;

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

    @Test
    void longPrincipalAndDeniedResponseFollowContract() throws Exception {
        when(jwt.validate("TEST_ONLY_ACCESS", TokenType.ACCESS)).thenReturn(new JwtClaims(42L,
            "TEST_ONLY_JTI", TokenType.ACCESS, Instant.now(), Instant.now().plusSeconds(1800)));
        mvc.perform(get("/api/test/principal").header("Authorization", "Bearer TEST_ONLY_ACCESS"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(42));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer TEST_ONLY_ACCESS")
                .contentType("application/json").content("{\"refreshToken\":\"TEST_ONLY_REFRESH\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"));
        org.mockito.Mockito.verify(tokens).logout(org.mockito.ArgumentMatchers.eq(42L),
            org.mockito.ArgumentMatchers.any(JwtClaims.class), org.mockito.ArgumentMatchers.eq("TEST_ONLY_REFRESH"));
        mvc.perform(get("/api/test/denied").header("Authorization", "Bearer TEST_ONLY_ACCESS"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void allowedCorsPreflightDoesNotNeedJwt() throws Exception {
        mvc.perform(options("/api/v1/auth/logout").header("Origin", "https://app.example")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "authorization,content-type"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://app.example"));
    }

    @Test
    void rejectedCorsAlsoUsesCommonJsonError() throws Exception {
        mvc.perform(options("/api/v1/auth/logout").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
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
