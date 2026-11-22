package com.ject.oogachaka.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PushRegistrationDeleteRequest(@Schema(description = "등록할 때 보낸 FCM 토큰") @NotBlank String token) {

    @Override
    public String toString() {
        return "PushRegistrationDeleteRequest[REDACTED]";
    }
}
