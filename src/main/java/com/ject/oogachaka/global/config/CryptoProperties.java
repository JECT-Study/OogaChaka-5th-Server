package com.ject.oogachaka.global.config;

import com.ject.oogachaka.global.crypto.HmacPurpose;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 환경변수로 받은 키를 시작 시 검증한다. 설정 객체의 문자열 표현에 키를 포함하지 않는다.
 */
@ConfigurationProperties(prefix = "app.crypto")
public class CryptoProperties {

    private final String activeVersion;
    private final Map<String, SecretKey> encryptionKeys;
    private final Map<HmacPurpose, SecretKey> hmacKeys;

    public CryptoProperties(String activeVersion, Map<String, String> aesKeys,
        String hmacSocialIdKey, String hmacDeviceTokenKey) {
        if (activeVersion == null || !activeVersion.matches("v[1-9][0-9]*")
            || aesKeys == null || aesKeys.isEmpty() || !aesKeys.containsKey(activeVersion)) {
            throw new IllegalStateException("app.crypto의 활성 AES 키 버전과 키 맵이 필요합니다.");
        }
        Map<String, SecretKey> keys = new LinkedHashMap<>();
        aesKeys.forEach((version, value) -> {
            if (version == null || !version.matches("v[1-9][0-9]*")) {
                throw new IllegalStateException("AES 키 버전은 v1, v2 형식이어야 합니다.");
            }
            keys.put(version, new SecretKeySpec(decode(value), "AES"));
        });
        SecretKey socialIdKey = new SecretKeySpec(decode(hmacSocialIdKey), "HmacSHA256");
        SecretKey deviceTokenKey = new SecretKeySpec(decode(hmacDeviceTokenKey), "HmacSHA256");
        if (sameKey(socialIdKey, deviceTokenKey)
            || keys.values().stream().anyMatch(key -> sameKey(key, socialIdKey) || sameKey(key, deviceTokenKey))) {
            throw new IllegalStateException("AES 키와 용도별 HMAC 키는 서로 다른 키여야 합니다.");
        }
        this.activeVersion = activeVersion;
        this.encryptionKeys = Map.copyOf(keys);
        this.hmacKeys = Map.of(HmacPurpose.SOCIAL_ID, socialIdKey, HmacPurpose.DEVICE_TOKEN, deviceTokenKey);
    }

    public String getActiveVersion() {
        return activeVersion;
    }

    public Map<String, SecretKey> getEncryptionKeys() {
        return encryptionKeys;
    }

    public SecretKey getHmacKey(HmacPurpose purpose) {
        return hmacKeys.get(purpose);
    }

    private static byte[] decode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("AES 및 HMAC 키 환경변수가 모두 필요합니다.");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("AES 및 HMAC 키는 유효한 Base64 형식이어야 합니다.");
        }
        if (key.length != 32) {
            throw new IllegalStateException("AES 및 HMAC 키는 Base64 디코딩 후 32바이트여야 합니다.");
        }
        return key;
    }

    private static boolean sameKey(SecretKey first, SecretKey second) {
        return MessageDigest.isEqual(first.getEncoded(), second.getEncoded());
    }
}
