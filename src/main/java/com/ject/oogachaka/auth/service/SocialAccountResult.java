package com.ject.oogachaka.auth.service;

// DB 엔티티나 제공자 원본 ID 대신 가입 결과에 필요한 사용자 ID와 이번 호출의 신규 생성 여부만 조정 서비스에 전달한다.
public record SocialAccountResult(Long userId, boolean isNewUser) {
}
