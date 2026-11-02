package com.ject.oogachaka.global.crypto;

import com.ject.oogachaka.global.config.CryptoProperties;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/** 테스트에서만 사용하는 반복 바이트 더미 키. 실제 환경에 사용하지 않는다. */
public final class TestCryptoKeys {

    private TestCryptoKeys() {
    }

    // 지정한 바이트를 반복해 재현 가능한 테스트 키를 만든다. value가 다르면 서로 다른 키가 된다.
    public static String dummyKey(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return Base64.getEncoder().encodeToString(bytes);
    }

    // 테스트에서도 AES·소셜 ID HMAC·디바이스 토큰 HMAC에 서로 다른 더미 키를 사용한다.
    public static CryptoProperties properties() {
        return new CryptoProperties("v1", Map.of("v1", dummyKey(1)), dummyKey(2), dummyKey(3));
    }
}
