package com.ject.oogachaka.auth.dto;

import com.ject.oogachaka.auth.enums.AuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SocialLoginRequest(
        @Schema(description = "소셜 제공자", example = "KAKAO") @NotNull AuthProvider provider,
        @Schema(description = "제공자 콜백 URL의 code 값") @NotBlank String code,
        @Schema(description = "콜백 URL의 state 값. authorize 응답의 state와 같아야 한다.", example = "Xb3fK9mQ2vN7pL1sR8tY4wZ6aC0dE5gH2jU9iO3kM7n")
        @NotBlank String state)
{
    @Override
    public String toString() {
        return "SocialLoginRequest[REDACTED]";
    }
}
