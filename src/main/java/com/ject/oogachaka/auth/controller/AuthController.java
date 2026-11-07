package com.ject.oogachaka.auth.controller;

import com.ject.oogachaka.auth.dto.AuthorizationResponse;
import com.ject.oogachaka.auth.dto.RefreshTokenRequest;
import com.ject.oogachaka.auth.dto.SocialLoginRequest;
import com.ject.oogachaka.auth.dto.SocialLoginResponse;
import com.ject.oogachaka.auth.dto.TokenResponse;
import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.auth.service.SocialLoginService;
import com.ject.oogachaka.auth.service.TokenService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP 입력·응답 변환만 맡고 외부 로그인, DB 저장, 토큰 처리는 각각의 서비스에 위임한다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SocialLoginService socialLoginService;
    private final TokenService tokenService;

    // 로그인 시작에 필요한 URL과 state를 조회하는 공개 API. 이 호출에서 앱 JWT를 발급하거나 HTTP 리다이렉트를 보내지는 않는다.
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

    // 현재 access 인증이 필요한 API다. 갱신 토큰도 본문으로 받아 두 토큰의 소유자를 비교한다.
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Long userId, @Valid @RequestBody RefreshTokenRequest request) {
        tokenService.logout(userId, request.refreshToken());
        return ApiResponse.success();
    }
}
