package com.ject.oogachaka.notification.dto;

import com.ject.oogachaka.notification.enums.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PushRegistrationRequest(
    @NotBlank String token,
    @NotNull DevicePlatform devicePlatform
) {
    @Override
    public String toString() {
        return "PushRegistrationRequest[REDACTED]";
    }
}
