package com.ject.oogachaka.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.ject.oogachaka.mission.event.MissionCompletionRequestedEvent;
import com.ject.oogachaka.notification.entity.DeviceToken;
import com.ject.oogachaka.notification.enums.DevicePlatform;
import com.ject.oogachaka.notification.service.PushNotification;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class NoOpPushSenderTest {

    @Test
    void skipsWithoutCredentialsAndNeverMarksTokensInvalidOrLogsTheirValues(CapturedOutput output) {
        DeviceToken device = DeviceToken.create(1L, "TEST_ONLY_SECRET_FCM_TOKEN", "hash", DevicePlatform.ANDROID);
        PushNotification notification = PushNotification.missionCompletionRequested(
            new MissionCompletionRequestedEvent(10L, 3L, "책 읽기"), "TEST_ONLY_NICKNAME");

        assertThat(new NoOpPushSender().send(List.of(device), notification)).isEmpty();

        assertThat(output).contains("푸시 비활성화로 발송 생략")
            .doesNotContain("TEST_ONLY_SECRET_FCM_TOKEN", "TEST_ONLY_NICKNAME", "책 읽기");
    }
}
