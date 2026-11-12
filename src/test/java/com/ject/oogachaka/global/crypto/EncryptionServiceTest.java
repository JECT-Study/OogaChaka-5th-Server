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

/** AES-GCM 저장 형식, 변조 거부, 키 교체와 Converter의 변환 규칙을 검증한다. 실제 JPA 주입이나 DB 컬럼 용량은 확인하지 않는다. */
class EncryptionServiceTest {

    private final EncryptionService service = new EncryptionService(TestCryptoKeys.properties());

    // 빈 문자열·영문·한글/이모지·앞뒤 공백을 암복호화해 원문을 그대로 복원한다.
    // 저장값이 v1 접두사와 12바이트 IV·암호문·16바이트 인증 태그로 구성되는지도 확인한다.
    @ParameterizedTest
    @ValueSource(strings = {"", "device-token", "한글 토큰 🔐", "  token  "})
    void encryptAndDecryptPreserveOriginal(String original) {
        String encrypted = service.encrypt(original);

        assertThat(encrypted).startsWith("v1:");
        assertThat(service.decrypt(encrypted)).isEqualTo(original);
        byte[] stored = Base64.getDecoder().decode(encrypted.substring(3));
        assertThat(stored).hasSize(12 + original.getBytes(StandardCharsets.UTF_8).length + 16);
    }

    // 같은 원문을 두 번 암호화해도 IV와 저장값이 달라야 하며, 두 값 모두 같은 원문으로 복원되어야 한다.
    @Test
    void repeatedEncryptionUsesDifferentIv() {
        String first = service.encrypt("same-token");
        String second = service.encrypt("same-token");

        assertThat(first).isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo(service.decrypt(second));
        assertThat(java.util.Arrays.copyOf(Base64.getDecoder().decode(first.substring(3)), 12))
            .isNotEqualTo(java.util.Arrays.copyOf(Base64.getDecoder().decode(second.substring(3)), 12));
    }

    // IV·암호문·인증 태그 중 어느 한 부분이라도 한 바이트를 변조하면 복호화를 거부한다.
    // offset 0은 IV, 12는 암호문, 32는 인증 태그 내부를 가리킨다.
    @ParameterizedTest
    @ValueSource(ints = {0, 12, 32})
    void rejectsTamperedIvCiphertextAndTag(int offset) {
        byte[] stored = Base64.getDecoder().decode(service.encrypt("dummy-token").substring(3));
        stored[offset] ^= 1;

        assertThatThrownBy(() -> service.decrypt("v1:" + Base64.getEncoder().encodeToString(stored)))
            .isInstanceOf(EncryptionException.class);
    }

    // 다른 AES 키로 복호화를 시도하면 원문이나 내부 예외를 드러내지 않고 공통 EncryptionException으로 실패해야 한다.
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

    // 평문, 빈 payload, 잘못된 Base64, 알 수 없는 버전, 짧은 저장값 등은 원문으로 대체하지 않고 복호화를 거부한다.
    @ParameterizedTest
    @ValueSource(strings = {"plaintext", "v1:", "v1:not-base64!", "v999:AAAA", "v1:AAAA", ":AAAA"})
    void rejectsInvalidFormatAndUnknownVersion(String encrypted) {
        assertThatThrownBy(() -> service.decrypt(encrypted)).isInstanceOf(EncryptionException.class);
    }

    // 활성 키를 v2로 바꾼 후에도 보관 중인 v1 키로 과거 값을 복호화하고 새 값은 v2로 저장해야 한다.
    // 키가 없거나 키 바이트가 같더라도 저장된 버전 접두사를 변조하면 복호화를 거부한다.
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

    // Converter가 저장 방향에서는 암호화하고 조회 방향에서는 복호화하며, null은 서비스와 Converter에서 그대로 유지해야 한다.
    // Converter를 직접 호출하는 단위 테스트이며 JPA의 Converter 자동 적용까지 검증하지는 않는다.
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
