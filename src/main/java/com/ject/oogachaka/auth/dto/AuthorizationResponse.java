package com.ject.oogachaka.auth.dto;

/**
 * 프런트가 이동할 인가 URL과 자신의 로그인 시도와 비교할 state를 반환한다. 서버용 verifier는 응답하지 않는다.
 */
public record AuthorizationResponse(
    String authorizationUrl,
    String state) {
}
