package com.ject.oogachaka.global.infrastructure.client.social;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 로그인 처리 중에만 사용하는 제공자 정보. 원문 ID/이메일의 toString을 제공하지 않는다. */
@Getter
@RequiredArgsConstructor
public class SocialUserInfo {
    // 외부에서 검증된 원본 소셜 ID다. 이 객체에서 HMAC 입력으로만 사용하고 DB·Redis에는 원문을 보관하지 않는다.
    private final String providerUserId;
    private final String nickname;
    private final String email;
}
