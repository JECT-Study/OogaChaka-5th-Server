package com.ject.oogachaka.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.ject.oogachaka.global.crypto.EncryptionException;
import com.ject.oogachaka.global.response.ApiResponse;
import com.ject.oogachaka.global.response.code.ErrorCode;
import jakarta.persistence.PersistenceException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** 중첩·순환 예외를 처리하는 공통 오류 응답을 검증한다. 보안 예외의 HTTP 처리는 MVC 보안 테스트에서 확인한다. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // 일반 예외와 JPA 예외 안에 복호화 실패가 감싸져 있어도 원인 체인을 찾아 CRYPTO_OPERATION_FAILED 응답을 반환해야 한다.
    @Test
    void wrappedJpaEncryptionFailureUsesCryptoResponse() throws Exception {
        RuntimeException exception = new RuntimeException("TEST_ONLY_OUTER_CONTEXT",
            new PersistenceException("TEST_ONLY_DATABASE_CONTEXT", new EncryptionException(EncryptionException.Operation.DECRYPT)));

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(exception);

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.CRYPTO_OPERATION_FAILED.getHttpStatus());
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.CRYPTO_OPERATION_FAILED.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.CRYPTO_OPERATION_FAILED.getMessage());
        assertThat(response.getBody().getData()).isNull();
    }

    // 예외 원인 체인이 서로를 참조해도 2초 안에 탐색을 끝내고 INTERNAL_SERVER_ERROR 응답을 반환해야 한다.
    @Test
    void cyclicCausesTerminateAndKeepGeneralServerResponse() {
        RuntimeException first = new RuntimeException("TEST_ONLY_FIRST");
        RuntimeException second = new RuntimeException("TEST_ONLY_SECOND", first);
        first.initCause(second);

        ResponseEntity<ApiResponse<Void>> response = assertTimeoutPreemptively(Duration.ofSeconds(2),
            () -> handler.handleException(first));

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus());
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
    }
}
