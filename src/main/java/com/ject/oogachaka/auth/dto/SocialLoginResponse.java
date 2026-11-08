package com.ject.oogachaka.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

// 앱 사용자 ID·이번 호출의 신규 가입 여부·앱 토큰을 반환한다. boolean 필드명이 newUser로 바뀌지 않도록 JSON 키 isNewUser를 명시했다.
public record SocialLoginResponse(
    @Schema(description = "앱 사용자 ID", example = "42") Long userId,
    @Schema(description = "신규 가입 여부", example = "true") @JsonProperty("isNewUser") boolean isNewUser,
    @Schema(description = "앱 access·refresh 토큰") TokenResponse tokens) {
}
