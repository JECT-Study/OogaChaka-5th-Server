package com.ject.oogachaka.auth.dto;

public record SocialLoginResponse(Long userId, boolean isNewUser, TokenResponse tokens) {
    @Override
    public String toString() {
        return "SocialLoginResponse[REDACTED]";
    }
}
