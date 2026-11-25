package com.ject.oogachaka.global.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ject.oogachaka.global.response.code.ErrorCode;
import com.ject.oogachaka.global.response.code.SuccessCode;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    private final String code;
    private final String message;
    private final T data;

    // JSON 본문에는 넣지 않고, ApiResponseStatusAdvice가 이 값을 응답의 HTTP 상태로 쓴다.
    @JsonIgnore
    private final HttpStatus httpStatus;

    public static ApiResponse<Void> success() {
        return success(SuccessCode.OK, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(SuccessCode.OK, data);
    }

    public static <T> ApiResponse<T> success(SuccessCode successCode, T data) {
        return ApiResponse.<T>builder()
            .code(successCode.getCode())
            .message(successCode.getMessage())
            .data(data)
            .httpStatus(successCode.getHttpStatus())
            .build();
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getMessage());
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return ApiResponse.<Void>builder()
            .code(errorCode.getCode())
            .message(message)
            .data(null)
            .httpStatus(errorCode.getHttpStatus())
            .build();
    }
}
