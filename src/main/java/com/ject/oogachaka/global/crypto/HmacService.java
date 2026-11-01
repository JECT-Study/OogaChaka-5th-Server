package com.ject.oogachaka.global.crypto;

import com.ject.oogachaka.global.config.CryptoProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 제공자의 원본 문자열을 UTF-8로 처리한다. trim, 대소문자 변경, Unicode 정규화를 하지 않는다.
 * 조회와 저장은 동일한 원본 및 용도를 전달하며, 이미 생성된 해시는 다시 입력하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class HmacService {

    private final CryptoProperties properties;

    public String hash(String value, HmacPurpose purpose) {
        if (value == null || purpose == null) {
            throw new EncryptionException(EncryptionException.Operation.HASH);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(properties.getHmacKey(purpose));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new EncryptionException(EncryptionException.Operation.HASH);
        }
    }
}
