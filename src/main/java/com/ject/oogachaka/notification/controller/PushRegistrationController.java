package com.ject.oogachaka.notification.controller;

import com.ject.oogachaka.global.response.ApiResponse;
import com.ject.oogachaka.notification.dto.PushRegistrationRequest;
import com.ject.oogachaka.notification.dto.PushUnregistrationRequest;
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

    private final PushRegistrationService service;

    @PutMapping
    public ApiResponse<Void> register(@AuthenticationPrincipal Long userId,
        @Valid @RequestBody PushRegistrationRequest request) {
        service.register(userId, request);
        return ApiResponse.success();
    }

    @DeleteMapping
    public ApiResponse<Void> unregister(@AuthenticationPrincipal Long userId,
        @Valid @RequestBody PushUnregistrationRequest request) {
        service.unregister(userId, request);
        return ApiResponse.success();
    }
}
