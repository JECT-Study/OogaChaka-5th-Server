package com.ject.oogachaka.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// 갱신·로그아웃의 refresh 본문이다. 누락·공백만 DTO가 검사하며 JWT 서명·종류·만료는 서비스가 확인한다.
public record RefreshTokenRequest(
    @Schema(description = "로그인·갱신 때 받은 refresh 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
    @NotBlank String refreshToken) {
}
