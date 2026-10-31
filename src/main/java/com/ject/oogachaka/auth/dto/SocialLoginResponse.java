package com.ject.oogachaka.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SocialLoginResponse(Long userId, @JsonProperty("isNewUser") boolean isNewUser, TokenResponse tokens) {
    @Override
    public String toString() {
        return "SocialLoginResponse[REDACTED]";
    }
}
