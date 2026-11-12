package com.ject.oogachaka.auth.token;

// 한 번의 로그인·갱신이 반환할 access와 refresh 묶음이다. 두 토큰을 연결하는 영속 세션 ID나 패밀리 추적 모델은 아니다.
public record TokenPair(IssuedToken access, IssuedToken refresh) {
}
