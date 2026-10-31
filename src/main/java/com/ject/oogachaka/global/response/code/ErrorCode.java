package com.ject.oogachaka.global.response.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "올바르지 않은 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "지원하지 않는 응답 형식입니다."),
    CONFLICT(HttpStatus.CONFLICT, "CONFLICT", "요청이 현재 리소스 상태와 충돌합니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 형식입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),
    CRYPTO_OPERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CRYPTO_OPERATION_FAILED", "암호화 처리 중 오류가 발생했습니다."),

    // 도메인 오류 코드는 아래에 도메인별로 추가한다.
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_EXPIRED", "인증 토큰이 만료되었습니다."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "유효하지 않은 인증 토큰입니다."),
    AUTH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_REVOKED", "로그아웃된 인증 토큰입니다."),
    AUTH_REFRESH_REUSED(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_REUSED", "사용할 수 없는 갱신 토큰입니다. 다시 로그인해 주세요."),
    AUTH_STATE_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_STATE_INVALID", "로그인 요청이 만료되었거나 유효하지 않습니다. 다시 시작해 주세요."),
    AUTH_PROVIDER_UNSUPPORTED(HttpStatus.BAD_REQUEST, "AUTH_PROVIDER_UNSUPPORTED", "지원하지 않는 소셜 로그인입니다."),
    AUTH_PROVIDER_DISABLED(HttpStatus.BAD_REQUEST, "AUTH_PROVIDER_DISABLED", "아직 사용할 수 없는 소셜 로그인입니다."),
    AUTH_SOCIAL_LOGIN_REJECTED(HttpStatus.BAD_REQUEST, "AUTH_SOCIAL_LOGIN_REJECTED", "소셜 로그인 요청이 거절되었습니다. 로그인을 다시 시작해 주세요."),
    AUTH_SOCIAL_LOGIN_FAILED(HttpStatus.BAD_GATEWAY, "AUTH_SOCIAL_LOGIN_FAILED", "소셜 로그인에 실패했습니다. 다시 시도해 주세요."),
    AUTH_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_PROVIDER_UNAVAILABLE", "소셜 로그인 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    AUTH_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_STORE_UNAVAILABLE", "인증 서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "존재하지 않는 사용자입니다."),
    DUPLICATE_USER(HttpStatus.CONFLICT, "DUPLICATE_USER", "중복된 사용자입니다."),
    INVALID_SCHEDULE_TIME(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE_TIME", "올바르지 않은 일정 시간입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    public static ErrorCode from(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> INVALID_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> CONFLICT;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status.is4xxClientError() ? INVALID_REQUEST : INTERNAL_SERVER_ERROR;
        };
    }
}
