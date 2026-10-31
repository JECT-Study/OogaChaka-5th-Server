package com.ject.oogachaka.global.infrastructure.client.social;

import com.ject.oogachaka.auth.enums.AuthProvider;

public interface SocialLoginClient {
    AuthProvider getProvider();

    boolean supportsPkce();

    SocialUserInfo getUserInfo(String code, String state, String verifier);
}
