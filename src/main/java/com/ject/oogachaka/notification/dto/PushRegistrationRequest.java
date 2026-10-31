package com.ject.oogachaka.notification.dto;

import com.ject.oogachaka.notification.enums.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PushRegistrationRequest(
    @NotBlank @Size(max = 4096) @Pattern(regexp = "\\S+") String token,
    @NotNull DevicePlatform devicePlatform
) {
    @Override
    public String toString() {
        return "PushRegistrationRequest[devicePlatform=" + devicePlatform + "]";
    }
}
