package com.ject.oogachaka.global.crypto;

import static com.ject.oogachaka.global.crypto.TestCryptoKeys.dummyKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ject.oogachaka.global.config.CryptoProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 소셜 ID 등의 동등 비교에 사용하는 HMAC의 결정성, 용도별 분리와 원문 입력 규칙을 검증한다. */
class HmacServiceTest {

    private final HmacService service = new HmacService(TestCryptoKeys.properties());

    // 같은 ID를 다시 조회할 수 있도록 동일 입력은 같은 소문자 hex 64자리 해시를 만들고, 다른 입력은 구분되어야 한다.
    @Test
    void sameInputProducesStableLowercaseHex() {
        String hash = service.hash("social-user-1", HmacPurpose.SOCIAL_ID);

        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(service.hash("social-user-1", HmacPurpose.SOCIAL_ID)).isEqualTo(hash);
        assertThat(service.hash("social-user-2", HmacPurpose.SOCIAL_ID)).isNotEqualTo(hash);
    }

    // 같은 원문이라도 소셜 ID용·디바이스 토큰용 키를 나누거나 키를 교체하면 해시가 달라야 한다.
    @Test
    void separatesKeysByPurposeAndChangesHashAfterKeyChange() {
        String hash = service.hash("same-input", HmacPurpose.SOCIAL_ID);
        HmacService changed = new HmacService(
            new CryptoProperties("v1", Map.of("v1", dummyKey(1)), dummyKey(4), dummyKey(3)));

        assertThat(service.hash("same-input", HmacPurpose.DEVICE_TOKEN)).isNotEqualTo(hash);
        assertThat(changed.hash("same-input", HmacPurpose.SOCIAL_ID)).isNotEqualTo(hash);
    }

    // 공백 제거·대소문자 변경·유니코드 정규화를 임의로 하지 않아 원문이 다른 ID를 같은 입력으로 취급하지 않아야 한다.
    @Test
    void preservesWhitespaceCaseAndUnicodeRepresentation() {
        String hash = service.hash("User", HmacPurpose.SOCIAL_ID);

        assertThat(service.hash(" User ", HmacPurpose.SOCIAL_ID)).isNotEqualTo(hash);
        assertThat(service.hash("user", HmacPurpose.SOCIAL_ID)).isNotEqualTo(hash);
        assertThat(service.hash("é", HmacPurpose.SOCIAL_ID))
            .isNotEqualTo(service.hash("e\u0301", HmacPurpose.SOCIAL_ID));
    }

    // 원본 ID나 HMAC 용도가 null이면 해시를 만들어 반환하지 않고 EncryptionException으로 실패해야 한다.
    @Test
    void rejectsMissingInputOrPurpose() {
        assertThatThrownBy(() -> service.hash(null, HmacPurpose.SOCIAL_ID)).isInstanceOf(EncryptionException.class);
        assertThatThrownBy(() -> service.hash("user", null)).isInstanceOf(EncryptionException.class);
    }
}
