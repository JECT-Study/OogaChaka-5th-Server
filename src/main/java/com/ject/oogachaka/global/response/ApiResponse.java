package com.ject.oogachaka.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ject.oogachaka.global.response.code.ErrorCode;
import com.ject.oogachaka.global.response.code.SuccessCode;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    private final String code;
    private final String message;
    private final T data;

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
            .build();
    }
}
