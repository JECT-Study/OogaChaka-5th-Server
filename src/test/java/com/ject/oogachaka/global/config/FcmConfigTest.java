package com.ject.oogachaka.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.FirebaseApp;
import com.ject.oogachaka.notification.infrastructure.NoOpPushSender;
import com.ject.oogachaka.notification.service.PushSender;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FcmConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(FcmConfig.class);

    @Test
    void disabledFcmDoesNotReadEvenInvalidCredentialConfiguration() {
        runner.withPropertyValues("app.fcm.enabled=false", "app.fcm.service-account-path=/dummy/missing.json",
            "app.fcm.service-account-json=TEST_ONLY_INVALID_JSON").run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(PushSender.class).doesNotHaveBean(FirebaseApp.class);
                assertThat(context.getBean(PushSender.class)).isInstanceOf(NoOpPushSender.class);
            });
    }

    @Test
    void missingEnabledPropertyDefaultsToNoOp() {
        runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(NoOpPushSender.class)
            .doesNotHaveBean(FirebaseApp.class));
    }

    @Test
    void enabledFcmRejectsConflictingCredentialsBeforeAccessingFilesOrNetwork() {
        runner.withPropertyValues("app.fcm.enabled=true", "app.fcm.service-account-path=/dummy/missing.json",
            "app.fcm.service-account-json=TEST_ONLY_INVALID_JSON")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidInlineCredentialsFailWithoutExposingTheirContent() {
        runner.withPropertyValues("app.fcm.enabled=true", "app.fcm.service-account-json=TEST_ONLY_INVALID_JSON")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageMatching("FCM 초기화에 실패했습니다\\. 프로젝트와 자격증명 설정을 확인해 주세요\\. "
                        + "원인 유형=[A-Za-z][A-Za-z0-9]*")
                    .hasMessageNotContaining("TEST_ONLY_INVALID_JSON").hasNoCause();
            });
    }

    @Test
    void missingCredentialFileReportsOnlyTheExceptionType(@TempDir Path directory) {
        String secretPath = directory.resolve("TEST_ONLY_PRIVATE_CREDENTIAL_PATH.json").toString();
        runner.withPropertyValues("app.fcm.enabled=true", "app.fcm.service-account-path=" + secretPath)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageEndingWith("원인 유형=NoSuchFileException")
                    .hasMessageNotContaining(secretPath).hasNoCause();
            });
    }
}
