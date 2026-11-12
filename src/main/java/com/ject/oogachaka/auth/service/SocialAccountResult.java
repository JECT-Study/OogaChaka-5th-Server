package com.ject.oogachaka.auth.service;

// 소셜 계정 조회·생성 결과. 로그인 응답에 필요한 사용자 ID와 신규 가입 여부만 담는다.
public record SocialAccountResult(Long userId, boolean isNewUser) {
}
