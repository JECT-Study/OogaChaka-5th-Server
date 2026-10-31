package com.ject.oogachaka.global.crypto;

import static com.ject.oogachaka.global.crypto.TestCryptoKeys.dummyKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ject.oogachaka.global.config.CryptoProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HmacServiceTest {

    private final HmacService service = new HmacService(TestCryptoKeys.properties());

    @Test
    void sameInputProducesStableLowercaseHex() {
        String hash = service.hash("social-user-1", HmacPurpose.SOCIAL_ID);

        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(service.hash("social-user-1", HmacPurpose.SOCIAL_ID)).isEqualTo(hash);
        assertThat(service.hash("social-user-2", HmacPurpose.SOCIAL_ID)).isNotEqualTo(hash);
    }

    @Test
    void separatesKeysByPurposeAndChangesHashAfterKeyChange() {
        String hash = service.hash("same-input", HmacPurpose.SOCIAL_ID);
        HmacService changed = new HmacService(
            new CryptoProperties("v1", Map.of("v1", dummyKey(1)), dummyKey(4), dummyKey(3)));

        assertThat(service.hash("same-input", HmacPurpose.DEVICE_TOKEN)).isNotEqualTo(hash);
        assertThat(changed.hash("same-input", HmacPurpose.SOCIAL_ID)).isNotEqualTo(hash);
    }

    @Test
    void preservesWhitespaceCaseAndUnicodeRepresentation() {
        String hash = service.hash("User", HmacPurpose.SOCIAL_ID);

        assertThat(service.hash(" User ", HmacPurpose.SOCIAL_ID)).isNotEqualTo(hash);
        assertThat(service.hash("user", HmacPurpose.SOCIAL_ID)).isNotEqualTo(hash);
        assertThat(service.hash("é", HmacPurpose.SOCIAL_ID))
            .isNotEqualTo(service.hash("e\u0301", HmacPurpose.SOCIAL_ID));
    }

    @Test
    void rejectsMissingInputOrPurpose() {
        assertThatThrownBy(() -> service.hash(null, HmacPurpose.SOCIAL_ID)).isInstanceOf(EncryptionException.class);
        assertThatThrownBy(() -> service.hash("user", null)).isInstanceOf(EncryptionException.class);
    }
}
