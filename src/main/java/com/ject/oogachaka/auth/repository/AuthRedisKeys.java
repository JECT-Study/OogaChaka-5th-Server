package com.ject.oogachaka.auth.repository;

/** 인증 Redis 키. 사용자별 hash tag로 rotation/logout의 모든 키를 같은 Cluster 슬롯에 둔다. */
public final class AuthRedisKeys {

    private static final String PREFIX = "oogachaka:auth:";
    private static final String STATE = PREFIX + "state:";
    private static final String REFRESH = ":refresh:";
    private static final String BLACKLIST = ":blacklist:";

    private AuthRedisKeys() {
    }

    public static String state(String state) {
        return STATE + state;
    }

    public static String refresh(Long userId, String jti) {
        return userPrefix(userId) + REFRESH + jti;
    }

    public static String blacklist(Long userId, String jti) {
        return userPrefix(userId) + BLACKLIST + jti;
    }

    private static String userPrefix(Long userId) {
        return PREFIX + "{user:" + userId + "}";
    }
}
