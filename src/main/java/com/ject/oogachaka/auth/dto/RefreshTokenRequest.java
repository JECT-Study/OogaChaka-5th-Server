package com.ject.oogachaka.auth.dto;

import jakarta.validation.constraints.NotBlank;

// [검토] 갱신·로그아웃의 refresh 본문이다. 누락·공백만 DTO가 검사하며 JWT 서명·종류·만료는 서비스가 확인한다.
public record RefreshTokenRequest(
    @NotBlank String refreshToken) {
}
