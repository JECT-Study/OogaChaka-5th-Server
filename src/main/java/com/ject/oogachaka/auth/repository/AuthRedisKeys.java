package com.ject.oogachaka.auth.repository;

/** 인증 Redis 키 형식을 한 곳에 고정해 저장·조회·삭제가 서로 다른 키를 만드는 오류를 줄인다. */
public final class AuthRedisKeys {

    // 같은 Redis를 공유하는 다른 기능과 인증 데이터를 구분하는 프로젝트 전용 접두사다.
    private static final String PREFIX = "oogachaka:auth:";
    private static final String STATE = PREFIX + "state:";
    private static final String REFRESH = PREFIX + "refresh:";

    private AuthRedisKeys() {
    }

    public static String state(String state) {
        return STATE + state;
    }

    // 같은 사용자도 토큰 발급마다 다른 jti로 구분해 여러 기기의 refresh를 독립 보관한다.
    public static String refresh(Long userId, String jti) {
        return REFRESH + userId + ":" + jti;
    }
}
