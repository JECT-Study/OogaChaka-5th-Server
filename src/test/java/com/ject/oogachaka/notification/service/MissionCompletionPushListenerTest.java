package com.ject.oogachaka.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.mission.event.MissionCompletionRequestedEvent;
import com.ject.oogachaka.notification.entity.DeviceToken;
import com.ject.oogachaka.notification.enums.DevicePlatform;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissionCompletionPushListenerTest {

    @Mock
    private ParentPushRecipientPort recipientPort;

    @Mock
    private PushSender pushSender;

    @Mock
    private DeviceTokenService deviceTokenService;

    @InjectMocks
    private MissionCompletionPushListener listener;

    private final MissionCompletionRequestedEvent event = new MissionCompletionRequestedEvent(10L, 3L, "책 읽기");

    @Test
    void sendsToAllDevicesReturnedByTheParentPortAndIncludesDeepLinkData() {
        var parentDevices = List.of(DeviceToken.create(1L, "dummy-parent-1", "hash-1", DevicePlatform.IOS),
            DeviceToken.create(2L, "dummy-parent-2", "hash-2", DevicePlatform.ANDROID));
        when(recipientPort.findByChildUserId(3L)).thenReturn(Optional.of(new ParentPushRecipients("자녀", parentDevices)));
        when(pushSender.send(eq(parentDevices), any())).thenReturn(List.of());

        listener.onMissionCompletionRequested(event);

        var payload = ArgumentCaptor.forClass(PushNotification.class);
        verify(pushSender).send(eq(parentDevices), payload.capture());
        assertThat(payload.getValue().title()).isEqualTo("미션 완료 인증 요청");
        assertThat(payload.getValue().body()).isEqualTo("자녀님이 '책 읽기' 완료 인증을 요청했어요");
        assertThat(payload.getValue().data()).containsEntry("type", "MISSION_COMPLETION_REQUESTED")
            .containsEntry("missionId", "10").containsEntry("childUserId", "3");
        verifyNoInteractions(deviceTokenService);
    }

    @Test
    void deletesOnlyDevicesReportedAsPermanentlyInvalid() {
        DeviceToken valid = DeviceToken.create(1L, "dummy-valid", "hash-1", DevicePlatform.IOS);
        DeviceToken invalid = DeviceToken.create(2L, "dummy-invalid", "hash-2", DevicePlatform.WEB);
        var devices = List.of(valid, invalid);
        when(recipientPort.findByChildUserId(3L)).thenReturn(Optional.of(new ParentPushRecipients("자녀", devices)));
        when(pushSender.send(eq(devices), any())).thenReturn(List.of(invalid));

        listener.onMissionCompletionRequested(event);

        verify(deviceTokenService).deleteInvalidRegistrations(List.of(invalid));
    }

    @Test
    void noParentsOrNoRegisteredDevicesSkipsSending() {
        when(recipientPort.findByChildUserId(3L)).thenReturn(Optional.empty())
            .thenReturn(Optional.of(new ParentPushRecipients("자녀", List.of())));

        listener.onMissionCompletionRequested(event);
        listener.onMissionCompletionRequested(event);

        verifyNoInteractions(pushSender, deviceTokenService);
    }

    @Test
    void lookupFailureDoesNotEscapeTheAfterCommitHandler() {
        when(recipientPort.findByChildUserId(3L)).thenThrow(new IllegalStateException("dummy-lookup-failure"));

        assertThatCode(() -> listener.onMissionCompletionRequested(event)).doesNotThrowAnyException();
        verifyNoInteractions(pushSender, deviceTokenService);
    }

    @Test
    void senderFailureDoesNotDeleteTokensOrEscapeTheHandler() {
        var devices = List.of(DeviceToken.create(1L, "dummy-token", "hash", DevicePlatform.WEB));
        when(recipientPort.findByChildUserId(3L)).thenReturn(Optional.of(new ParentPushRecipients("자녀", devices)));
        when(pushSender.send(eq(devices), any())).thenThrow(new IllegalStateException("dummy-provider-failure"));

        assertThatCode(() -> listener.onMissionCompletionRequested(event)).doesNotThrowAnyException();
        verifyNoInteractions(deviceTokenService);
    }
}
