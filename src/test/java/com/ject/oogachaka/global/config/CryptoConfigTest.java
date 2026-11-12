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

/** 암호화 키 설정과 Jasypt 초기화가 잘못된 환경에서 시작을 막는지 검증한다. 모든 키와 비밀번호는 테스트용 더미다. */
class CryptoConfigTest {

    // AES 또는 용도별 HMAC 키가 null·빈 값·공백·잘못된 Base64·짧은 바이트 배열이면 설정 생성을 거부한다.
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

    // AES 키가 32바이트를 넘거나, 활성 버전에 해당하는 키가 없거나, 키 맵이 비어 있으면 시작 전에 거부한다.
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

    // AES와 HMAC, 서로 다른 HMAC 용도 사이에 같은 키를 재사용하면 거부한다.
    // 비활성 AES 키도 검사해 과거 복호화용 키와 현재 HMAC 키가 겹치는 경우를 놓치지 않아야 한다.
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

    // 키 버전이 누락되거나 v0·v01·구분자 포함 값이면 거부해 저장 형식의 버전 식별이 모호해지지 않도록 한다.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"v0", "v01", "v1:other"})
    void rejectsInvalidVersion(String version) {
        assertThatThrownBy(() -> new CryptoProperties(version, Collections.singletonMap(version, dummyKey(1)),
            dummyKey(2), dummyKey(3)))
            .isInstanceOf(IllegalStateException.class);
    }

    // 정상 설정은 Spring 빈에 바인딩되고 키 값이 문자열 표현에 드러나지 않아야 한다.
    // 필수 HMAC 키가 누락되면 Spring 컨텍스트 시작이 실패하는지 확인한다.
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

    // ENC 설정이 아직 없어도 Jasypt 비밀번호가 빠졌으면 빈 생성 단계에서 실패해야 한다.
    // 나중에 암호화 설정을 추가했을 때까지 비밀번호 누락을 숨기지 않는지 확인한다.
    @Test
    void jasyptBeanRejectsMissingPasswordWithoutEncryptedProperties() {
        new ApplicationContextRunner().withUserConfiguration(JasyptConfig.class)
            .withPropertyValues("jasypt.encryptor.password=")
            .run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageStartingWith("Jasypt 비밀번호가 없습니다."));
    }

    // 로컬 설정 파일을 흉내 낸 프로퍼티 소스의 비밀번호로 ENC 값을 복호화해 Spring 설정에서 읽을 수 있어야 한다.
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

    // 공유 application.properties에 Jasypt 비밀번호가 정의되어 있으면 ENC처럼 생긴 값이어도 복호화 시도 전에 시작을 거부한다.
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
