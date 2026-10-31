package com.ject.oogachaka.auth.dto;

public record AuthorizationResponse(String authorizationUrl, String state) {
    @Override
    public String toString() {
        return "AuthorizationResponse[REDACTED]";
    }
}
