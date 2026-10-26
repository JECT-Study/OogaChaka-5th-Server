package com.ject.oogachaka.global.exception;

import com.ject.oogachaka.global.response.code.ErrorCode;
import com.ject.oogachaka.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
            .body(ApiResponse.error(errorCode));
    }

    // Spring MVC의 요청 오류를 공통 응답으로 변환하며 원래 상태 코드와 헤더를 유지한다.
    @Override
    protected ResponseEntity<Object> createResponseEntity(
        Object body,
        HttpHeaders headers,
        HttpStatusCode statusCode,
        WebRequest request
    ) {
        return new ResponseEntity<>(ApiResponse.error(ErrorCode.from(statusCode)), headers, statusCode);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) throws Exception {
        // 보안 예외는 Spring Security 필터에서 처리하도록 전달한다.
        if (exception instanceof AuthenticationException || exception instanceof AccessDeniedException) {
            throw exception;
        }

        log.error("처리되지 않은 서버 오류가 발생했습니다.", exception);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getHttpStatus())
            .body(ApiResponse.error(errorCode));
    }
}
