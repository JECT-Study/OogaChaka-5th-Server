package com.ject.oogachaka.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 프런트가 이동할 인가 URL과 자신의 로그인 시도와 비교할 state를 반환한다. 서버용 verifier는 응답하지 않는다.
 */
public record AuthorizationResponse(
    @Schema(description = "브라우저로 열 제공자 로그인 페이지 URL", example = "https://kauth.kakao.com/oauth/authorize?response_type=code&...")
    String authorizationUrl,
    @Schema(description = "이번 로그인 시도의 state. 프런트가 보관했다가 콜백 state와 비교하고 social-login에 보낸다.",
        example = "Xb3fK9mQ2vN7pL1sR8tY4wZ6aC0dE5gH2jU9iO3kM7n")
    String state) {
}
