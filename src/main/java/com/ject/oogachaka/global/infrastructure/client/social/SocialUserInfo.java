package com.ject.oogachaka.global.infrastructure.client.social;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 로그인 처리 중에만 사용하는 제공자 정보. 원문 ID/이메일의 toString을 제공하지 않는다. */
@Getter
@RequiredArgsConstructor
public class SocialUserInfo {
    private final String providerUserId;
    private final String nickname;
    private final String email;
}
