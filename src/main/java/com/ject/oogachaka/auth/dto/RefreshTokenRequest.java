package com.ject.oogachaka.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(@NotBlank @Size(max = 4096) String refreshToken) {
    @Override
    public String toString() {
        return "RefreshTokenRequest[REDACTED]";
    }
}
