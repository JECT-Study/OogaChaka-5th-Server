package com.ject.oogachaka.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record PushUnregistrationRequest(@NotBlank String token) {

    @Override
    public String toString() {
        return "PushUnregistrationRequest[REDACTED]";
    }
}
