package com.ject.oogachaka.global.crypto;

import static com.ject.oogachaka.global.crypto.TestCryptoKeys.dummyKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ject.oogachaka.global.config.CryptoProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EncryptionServiceTest {

    private final EncryptionService service = new EncryptionService(TestCryptoKeys.properties());

    @ParameterizedTest
    @ValueSource(strings = {"", "device-token", "한글 토큰 🔐", "  token  "})
    void encryptAndDecryptPreserveOriginal(String original) {
        String encrypted = service.encrypt(original);

        assertThat(encrypted).startsWith("v1:");
        assertThat(service.decrypt(encrypted)).isEqualTo(original);
        byte[] stored = Base64.getDecoder().decode(encrypted.substring(3));
        assertThat(stored).hasSize(12 + original.getBytes(StandardCharsets.UTF_8).length + 16);
    }

    @Test
    void repeatedEncryptionUsesDifferentIv() {
        String first = service.encrypt("same-token");
        String second = service.encrypt("same-token");

        assertThat(first).isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo(service.decrypt(second));
        assertThat(java.util.Arrays.copyOf(Base64.getDecoder().decode(first.substring(3)), 12))
            .isNotEqualTo(java.util.Arrays.copyOf(Base64.getDecoder().decode(second.substring(3)), 12));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 12, 32})
    void rejectsTamperedIvCiphertextAndTag(int offset) {
        byte[] stored = Base64.getDecoder().decode(service.encrypt("dummy-token").substring(3));
        stored[offset] ^= 1;

        assertThatThrownBy(() -> service.decrypt("v1:" + Base64.getEncoder().encodeToString(stored)))
            .isInstanceOf(EncryptionException.class);
    }

    @Test
    void rejectsWrongKeyWithoutExposingInput() {
        EncryptionService other = new EncryptionService(
            new CryptoProperties("v1", Map.of("v1", dummyKey(4)), dummyKey(2), dummyKey(3)));
        String encrypted = service.encrypt("sensitive-dummy-token");

        assertThatThrownBy(() -> other.decrypt(encrypted))
            .isInstanceOf(EncryptionException.class)
            .hasMessage("암호화 처리에 실패했습니다.")
            .hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {"plaintext", "v1:", "v1:not-base64!", "v999:AAAA", "v1:AAAA", ":AAAA"})
    void rejectsInvalidFormatAndUnknownVersion(String encrypted) {
        assertThatThrownBy(() -> service.decrypt(encrypted)).isInstanceOf(EncryptionException.class);
    }

    @Test
    void keyRotationRetainsOldDecryptionKeyAndAuthenticatesVersion() {
        String oldValue = service.encrypt("old-token");
        EncryptionService rotated = new EncryptionService(
            new CryptoProperties("v2", Map.of("v1", dummyKey(1), "v2", dummyKey(4)), dummyKey(2), dummyKey(3)));

        assertThat(rotated.decrypt(oldValue)).isEqualTo("old-token");
        String newValue = rotated.encrypt("new-token");
        assertThat(newValue).startsWith("v2:");
        assertThat(rotated.decrypt(newValue)).isEqualTo("new-token");
        assertThatThrownBy(() -> service.decrypt(newValue)).isInstanceOf(EncryptionException.class);

        EncryptionService aliases = new EncryptionService(
            new CryptoProperties("v1", Map.of("v1", dummyKey(1), "v2", dummyKey(1)), dummyKey(2), dummyKey(3)));
        assertThatThrownBy(() -> aliases.decrypt(oldValue.replaceFirst("v1:", "v2:")))
            .isInstanceOf(EncryptionException.class);
    }

    @Test
    void converterEncryptsStoredValuesAndPreservesNull() {
        EncryptionConverter converter = new EncryptionConverter(service);
        String encrypted = converter.convertToDatabaseColumn("token");

        assertThat(service.encrypt(null)).isNull();
        assertThat(service.decrypt(null)).isNull();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(encrypted).startsWith("v1:").isNotEqualTo("token");
        assertThat(service.decrypt(encrypted)).isEqualTo("token");
        assertThat(converter.convertToEntityAttribute(service.encrypt("token"))).isEqualTo("token");
    }
}
