package com.ject.oogachaka.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.FirebaseApp;
import com.ject.oogachaka.notification.infrastructure.NoOpPushSender;
import com.ject.oogachaka.notification.service.PushSender;
import org.junit.jupiter.api.Test;
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
                assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "FCM 초기화에 실패했습니다. 프로젝트와 자격증명 설정을 확인해 주세요.");
                assertThat(context.getStartupFailure().getCause().getMessage()).doesNotContain("TEST_ONLY_INVALID_JSON");
            });
    }
}
