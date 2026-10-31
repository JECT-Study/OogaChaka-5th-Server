package com.ject.oogachaka.auth.service;

import com.ject.oogachaka.auth.enums.AuthProvider;

public record OAuthState(AuthProvider provider, String verifier) {
    @Override
    public String toString() {
        return "OAuthState[REDACTED]";
    }
}
