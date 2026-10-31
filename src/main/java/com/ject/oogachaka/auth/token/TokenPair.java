package com.ject.oogachaka.auth.token;

public record TokenPair(IssuedToken access, IssuedToken refresh) {
    @Override
    public String toString() {
        return "TokenPair[REDACTED]";
    }
}
