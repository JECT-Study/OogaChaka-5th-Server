package com.ject.oogachaka.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.ject.oogachaka.global.crypto.EncryptionException;
import com.ject.oogachaka.global.response.ApiResponse;
import com.ject.oogachaka.global.response.code.ErrorCode;
import jakarta.persistence.PersistenceException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void wrappedJpaEncryptionFailureUsesCryptoResponseWithoutWrapperMessage() throws Exception {
        RuntimeException exception = new RuntimeException("TEST_ONLY_OUTER_CONTEXT",
            new PersistenceException("TEST_ONLY_DATABASE_CONTEXT", new EncryptionException(EncryptionException.Operation.DECRYPT)));

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(exception);

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.CRYPTO_OPERATION_FAILED.getHttpStatus());
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.CRYPTO_OPERATION_FAILED.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.CRYPTO_OPERATION_FAILED.getMessage());
        assertThat(response.getBody().getData()).isNull();
    }

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

    @Test
    void securityFailureStillPropagatesToSecurityFilter() {
        AccessDeniedException exception = new AccessDeniedException("TEST_ONLY_DENIED");

        assertThatThrownBy(() -> handler.handleException(exception)).isSameAs(exception);
    }
}
