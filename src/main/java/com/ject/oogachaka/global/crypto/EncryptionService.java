package com.ject.oogachaka.global.crypto;

import com.ject.oogachaka.global.config.CryptoProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.stereotype.Service;

/**
 * 버전:Base64(12바이트 IV + 암호문 + 16바이트 태그) 형식의 AES-256-GCM 암호화
 */
@Service
public class EncryptionService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 16;

    private final CryptoProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(CryptoProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String value) {
        if (value == null) {
            return null;
        }
        String version = properties.getActiveVersion();
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = createCipher(Cipher.ENCRYPT_MODE, properties.getEncryptionKeys().get(version), iv, version);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] stored = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return version + ":" + Base64.getEncoder().encodeToString(stored);
        } catch (GeneralSecurityException exception) {
            throw new EncryptionException(EncryptionException.Operation.ENCRYPT);
        }
    }

    public String decrypt(String value) {
        if (value == null) {
            return null;
        }
        try {
            int separator = value.indexOf(':');
            if (separator < 1) {
                throw new EncryptionException(EncryptionException.Operation.DECRYPT);
            }
            String version = value.substring(0, separator);
            SecretKey key = properties.getEncryptionKeys().get(version);
            if (key == null) {
                throw new EncryptionException(EncryptionException.Operation.DECRYPT);
            }
            byte[] stored = Base64.getDecoder().decode(value.substring(separator + 1));
            if (stored.length < IV_LENGTH + TAG_LENGTH) {
                throw new EncryptionException(EncryptionException.Operation.DECRYPT);
            }
            byte[] iv = new byte[IV_LENGTH];
            ByteBuffer.wrap(stored).get(iv);
            Cipher cipher = createCipher(Cipher.DECRYPT_MODE, key, iv, version);
            return new String(cipher.doFinal(stored, IV_LENGTH, stored.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new EncryptionException(EncryptionException.Operation.DECRYPT);
        }
    }

    private Cipher createCipher(int mode, SecretKey key, byte[] iv, String version) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_LENGTH * Byte.SIZE, iv));
        // 버전 접두어도 인증하여 헤더 변경을 감지한다.
        cipher.updateAAD(version.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
