package com.ject.oogachaka.global.config;

import static com.ject.oogachaka.global.crypto.TestCryptoKeys.dummyKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ject.oogachaka.global.crypto.HmacPurpose;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CryptoPropertiesTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-base64!", "ZHVtbXk="})
    void rejectsMissingInvalidOrShortKeys(String invalid) {
        assertThatThrownBy(() -> new CryptoProperties("v1", Map.of("v1", dummyKey(1)), invalid, dummyKey(3)))
            .isInstanceOf(IllegalStateException.class).hasNoCause();
        assertThatThrownBy(() -> new CryptoProperties("v1", Map.of("v1", dummyKey(1)), dummyKey(2), invalid))
            .isInstanceOf(IllegalStateException.class);
        Map<String, String> aesKeys = new java.util.HashMap<>();
        aesKeys.put("v1", invalid);
        assertThatThrownBy(() -> new CryptoProperties("v1", aesKeys, dummyKey(2), dummyKey(3)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsLongKeyAndMissingActiveVersion() {
        String longKey = Base64.getEncoder().encodeToString(new byte[33]);
        assertThatThrownBy(() -> new CryptoProperties("v1", Map.of("v1", longKey), dummyKey(2), dummyKey(3)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CryptoProperties("v2", Map.of("v1", dummyKey(1)), dummyKey(2), dummyKey(3)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CryptoProperties("v1", Map.of(), dummyKey(2), dummyKey(3)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CryptoProperties("v1", null, dummyKey(2), dummyKey(3)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsSharedKeysIncludingInactiveAesKey() {
        assertThatThrownBy(() -> new CryptoProperties("v1", Map.of("v1", dummyKey(1)), dummyKey(1), dummyKey(3)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CryptoProperties("v1", Map.of("v1", dummyKey(1)), dummyKey(2), dummyKey(1)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CryptoProperties("v1", Map.of("v1", dummyKey(1)), dummyKey(2), dummyKey(2)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CryptoProperties("v2", Map.of("v1", dummyKey(2), "v2", dummyKey(1)),
            dummyKey(2), dummyKey(3))).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"v0", "v01", "v1:other"})
    void rejectsInvalidVersion(String version) {
        assertThatThrownBy(() -> new CryptoProperties(version, Map.of("v1", dummyKey(1)), dummyKey(2), dummyKey(3)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bindsAndValidatesAtContextStartup() {
        new ApplicationContextRunner().withUserConfiguration(CryptoConfig.class)
            .withPropertyValues("app.crypto.active-version=v1", "app.crypto.aes-keys.v1=" + dummyKey(1),
                "app.crypto.hmac-social-id-key=" + dummyKey(2), "app.crypto.hmac-device-token-key=" + dummyKey(3))
            .run(context -> {
                assertThat(context).hasNotFailed();
                CryptoProperties properties = context.getBean(CryptoProperties.class);
                assertThat(properties.getActiveVersion()).isEqualTo("v1");
                assertThat(properties.getHmacKey(HmacPurpose.SOCIAL_ID).getEncoded()).hasSize(32);
                assertThat(properties.toString()).doesNotContain(dummyKey(1), dummyKey(2), dummyKey(3));
            });
        new ApplicationContextRunner().withUserConfiguration(CryptoConfig.class)
            .withPropertyValues("app.crypto.active-version=v1", "app.crypto.aes-keys.v1=" + dummyKey(1))
            .run(context -> assertThat(context).hasFailed());
    }
}
