package com.ject.oogachaka.global.crypto;

import lombok.Getter;

/**
 * 원문, 키, 암호문이나 공급자의 예외 메시지를 포함하지 않는 공통 암호화 오류
 */
@Getter
public class EncryptionException extends RuntimeException {

    private final Operation operation;

    public EncryptionException(Operation operation) {
        super("암호화 처리에 실패했습니다.");
        this.operation = operation;
    }

    public enum Operation {
        ENCRYPT,
        DECRYPT,
        HASH
    }
}
