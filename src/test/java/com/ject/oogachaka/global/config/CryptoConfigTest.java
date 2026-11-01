package com.ject.oogachaka.global.config;

import static com.ject.oogachaka.global.crypto.TestCryptoKeys.dummyKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ject.oogachaka.global.crypto.HmacPurpose;
import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;

class CryptoConfigTest {

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
        assertThatThrownBy(() -> new CryptoProperties(version, Collections.singletonMap(version, dummyKey(1)),
            dummyKey(2), dummyKey(3)))
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

    @Test
    void jasyptBeanRejectsMissingPasswordWithoutEncryptedProperties() {
        new ApplicationContextRunner().withUserConfiguration(JasyptConfig.class)
            .withPropertyValues("jasypt.encryptor.password=")
            .run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageStartingWith("Jasypt 비밀번호가 없습니다."));
    }

    @Test
    void jasyptDecryptsPropertiesWithLocalPassword() {
        String password = "TEST_ONLY_JASYPT_PASSWORD";
        String encrypted = JasyptEncryptorFactory.create(password).encrypt("TEST_ONLY_SECRET");

        new ApplicationContextRunner().withUserConfiguration(JasyptConfig.class, EncryptablePropertiesConfig.class)
            .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "Config resource 'class path resource [application-local.properties]'",
                Map.of("jasypt.encryptor.password", password))))
            .withPropertyValues("test.secret=ENC(" + encrypted + ")")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getEnvironment().getProperty("test.secret")).isEqualTo("TEST_ONLY_SECRET");
            });
    }

    @Test
    void jasyptRejectsPasswordInSharedConfigBeforeDecryption() {
        new ApplicationContextRunner().withUserConfiguration(JasyptConfig.class, EncryptablePropertiesConfig.class)
            .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "Config resource 'class path resource [application.properties]'",
                Map.of("jasypt.encryptor.password", "ENC(TEST_ONLY_INVALID_CIPHERTEXT)"))))
            .run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageStartingWith("Jasypt 비밀번호를 Git에 올라가는 application.properties에 적으면 안 됩니다."));
    }

    @EnableEncryptableProperties
    static class EncryptablePropertiesConfig {
    }
}
