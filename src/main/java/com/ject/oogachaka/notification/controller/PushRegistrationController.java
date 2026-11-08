package com.ject.oogachaka.notification.controller;

import com.ject.oogachaka.global.response.ApiResponse;
import com.ject.oogachaka.notification.dto.PushRegistrationRequest;
import com.ject.oogachaka.notification.dto.PushRegistrationDeleteRequest;
import com.ject.oogachaka.notification.service.PushRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/push/registrations")
@RequiredArgsConstructor
public class PushRegistrationController {

    private final PushRegistrationService pushRegistrationService;

    // 로그인 직후, 앱 시작할 때(로그인 상태), FCM 토큰 갱신 시 호출
    @PutMapping
    public ApiResponse<Void> register(@AuthenticationPrincipal Long userId, @Valid @RequestBody PushRegistrationRequest request) {
        pushRegistrationService.register(userId, request);
        return ApiResponse.success();
    }

    // 로그아웃 직전에 한번 호출
    @DeleteMapping
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId, @Valid @RequestBody PushRegistrationDeleteRequest request) {
        pushRegistrationService.delete(userId, request);
        return ApiResponse.success();
    }
}
