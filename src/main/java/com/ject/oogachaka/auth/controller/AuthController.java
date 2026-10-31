package com.ject.oogachaka.auth.controller;

import com.ject.oogachaka.auth.dto.AuthorizationResponse;
import com.ject.oogachaka.auth.dto.RefreshTokenRequest;
import com.ject.oogachaka.auth.dto.SocialLoginRequest;
import com.ject.oogachaka.auth.dto.SocialLoginResponse;
import com.ject.oogachaka.auth.dto.TokenResponse;
import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.auth.security.JwtAuthenticationFilter;
import com.ject.oogachaka.auth.service.SocialLoginService;
import com.ject.oogachaka.auth.service.TokenService;
import com.ject.oogachaka.auth.token.JwtClaims;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.ApiResponse;
import com.ject.oogachaka.global.response.code.ErrorCode;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SocialLoginService socialLoginService;
    private final TokenService tokenService;

    @GetMapping("/{provider}/authorize")
    public ApiResponse<AuthorizationResponse> authorize(@PathVariable String provider) {
        AuthProvider parsed;
        try {
            parsed = AuthProvider.valueOf(provider.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUTH_PROVIDER_UNSUPPORTED);
        }
        return ApiResponse.success(socialLoginService.authorize(parsed));
    }

    @PostMapping("/social-login")
    public ApiResponse<SocialLoginResponse> socialLogin(@Valid @RequestBody SocialLoginRequest request) {
        return ApiResponse.success(socialLoginService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(tokenService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Long userId,
        @RequestAttribute(JwtAuthenticationFilter.ACCESS_CLAIMS_ATTRIBUTE) JwtClaims access,
        @Valid @RequestBody RefreshTokenRequest request) {
        tokenService.logout(userId, access, request.refreshToken());
        return ApiResponse.success();
    }
}
