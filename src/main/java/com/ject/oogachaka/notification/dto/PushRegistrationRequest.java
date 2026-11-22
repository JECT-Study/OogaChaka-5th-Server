package com.ject.oogachaka.notification.dto;

import com.ject.oogachaka.notification.enums.DevicePlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PushRegistrationRequest(
    @Schema(description = "Firebase Messaging에서 받은 FCM 토큰. iOS의 APNs 토큰이 아니다.") @NotBlank String token,
    @Schema(description = "기기 플랫폼. 대소문자를 구분하지 않는다.", example = "IOS") @NotNull DevicePlatform devicePlatform
) {
    @Override
    public String toString() {
        return "PushRegistrationRequest[REDACTED]";
    }
}
