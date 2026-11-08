package com.ject.oogachaka.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record PushRegistrationDeleteRequest(@NotBlank String token) {

    @Override
    public String toString() {
        return "PushRegistrationDeleteRequest[REDACTED]";
    }
}
