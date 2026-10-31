package com.ject.oogachaka.auth.dto;

import com.ject.oogachaka.auth.enums.AuthProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SocialLoginRequest(@NotNull AuthProvider provider, @NotBlank @Size(max = 4096) String code,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String state) {
    @Override
    public String toString() {
        return "SocialLoginRequest[REDACTED]";
    }
}
