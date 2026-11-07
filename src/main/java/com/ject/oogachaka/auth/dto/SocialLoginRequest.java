package com.ject.oogachaka.auth.dto;

import com.ject.oogachaka.auth.enums.AuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SocialLoginRequest(
        @Schema(description = "소셜 제공자", example = "KAKAO")
        @NotNull AuthProvider provider,
        @Schema(description = "제공자 콜백 URL의 code 값")
        @NotBlank String code,
        // 서버가 발급하는 32바이트 Base64 URL state 형식만 받는다. 이 형식 검사만으로 시도 소유자나 재사용 여부를 검증한 것은 아니다.
        @Schema(description = "콜백 URL의 state 값. authorize 응답의 state와 같아야 한다.", example = "Xb3fK9mQ2vN7pL1sR8tY4wZ6aC0dE5gH2jU9iO3kM7n")
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String state)
{
    @Override
    public String toString() {
        // 민감한 입력 원문을 넣지 않는 고정 표시다.
        return "SocialLoginRequest[REDACTED]";
    }
}
