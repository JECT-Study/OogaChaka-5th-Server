package com.ject.oogachaka.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** DB에 연결하지 않고 실제 설정 파일의 프로필별 선택과 운영 필수 값 누락을 검사한다. */
class ProfileConfigurationTest {

    @TempDir
    Path temporaryDirectory;

    private Path personalConfig;
    private ApplicationContextRunner runner;

    @BeforeEach
    void useTemporaryPersonalConfig() throws IOException {
        personalConfig = Files.createFile(temporaryDirectory.resolve("personal.properties"));
        runner = new ApplicationContextRunner()
            .withPropertyValues("app.local-config=" + personalConfig.toUri())
            .withInitializer(new ConfigDataApplicationContextInitializer());
    }

    // 프로필을 생략한 실행은 팀 DB 암호문 대신 내 맥 DB 주소를 선택한다.
    @Test
    void defaultsToLocalDatabase() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getDefaultProfiles()).containsExactly("local");
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:mysql://127.0.0.1:3306/oogachaka");
        });
    }

    // 임시 개인 파일은 local 기본 주소를 덮어쓰지만 dev의 팀 DB 설정에는 영향을 주지 않는다.
    @Test
    void personalOverridesApplyOnlyToLocal() throws IOException {
        Files.writeString(personalConfig, "spring.datasource.url=jdbc:mysql://personal.invalid:13306/test\n");
        runner.run(context -> assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
            .isEqualTo("jdbc:mysql://personal.invalid:13306/test"));
        runner.withPropertyValues("spring.profiles.active=dev").run(context ->
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).startsWith("ENC("));
    }

    // dev는 기존 DB 암호문을 유지하며 local의 마이그레이션 설정을 가져오지 않는다.
    @Test
    void devKeepsExistingEncryptedConfiguration() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).startsWith("ENC(");
            assertThat(context.getEnvironment().getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("true");
        });
    }

    // SSM에서 주입한 환경변수는 dev의 DB 암호문보다 우선하고 prod에서도 그대로 사용된다.
    @Test
    void runtimeEnvironmentOverridesProfileDefaults() {
        for (String profile : new String[] {"dev", "prod"}) {
            runner.withPropertyValues("spring.profiles.active=" + profile)
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                    new SystemEnvironmentPropertySource("runtime", Map.of(
                        "SPRING_DATASOURCE_URL", "jdbc:mysql://runtime.invalid/oogachaka",
                        "SPRING_DATASOURCE_USERNAME", "TEST_ONLY_USER",
                        "SPRING_DATASOURCE_PASSWORD", "TEST_ONLY_PASSWORD"))))
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                        .isEqualTo("jdbc:mysql://runtime.invalid/oogachaka");
                    assertThat(context.getEnvironment().getProperty("spring.datasource.username"))
                        .isEqualTo("TEST_ONLY_USER");
                });
        }
    }

    // prod에 운영 DB 주소가 빠졌을 때 테스트 DB 암호문으로 대체하지 않는다.
    @Test
    void prodRequiresItsOwnDatabase() {
        runner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            assertThatThrownBy(() -> context.getEnvironment().getProperty("spring.datasource.url"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(context.getEnvironment().getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("false");
        });
    }
}
