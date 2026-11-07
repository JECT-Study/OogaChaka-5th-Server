package com.ject.oogachaka.auth.dto;

import com.ject.oogachaka.auth.enums.AuthProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SocialLoginRequest(
        @NotNull AuthProvider provider,
        @NotBlank String code,
        // 서버가 발급하는 32바이트 Base64 URL state 형식만 받는다. 이 형식 검사만으로 시도 소유자나 재사용 여부를 검증한 것은 아니다.
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String state)
{
    @Override
    public String toString() {
        // 민감한 입력 원문을 넣지 않는 고정 표시다.
        return "SocialLoginRequest[REDACTED]";
    }
}
