package com.ject.oogachaka.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// 앱 사용자 ID·이번 호출의 신규 가입 여부·앱 토큰을 반환한다. boolean 필드명이 newUser로 바뀌지 않도록 JSON 키 isNewUser를 명시했다.
public record SocialLoginResponse(
    Long userId,
    @JsonProperty("isNewUser") boolean isNewUser,
    TokenResponse tokens) {
}
