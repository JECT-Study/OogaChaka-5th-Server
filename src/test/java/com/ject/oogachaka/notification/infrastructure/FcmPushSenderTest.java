package com.ject.oogachaka.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.ject.oogachaka.mission.event.MissionCompletionRequestedEvent;
import com.ject.oogachaka.notification.entity.DeviceToken;
import com.ject.oogachaka.notification.enums.DevicePlatform;
import com.ject.oogachaka.notification.service.PushNotification;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FcmPushSenderTest {

    @Mock
    private FirebaseMessaging messaging;

    @Captor
    private ArgumentCaptor<List<Message>> messages;

    private FcmPushSender sender;
    private final PushNotification notification = PushNotification.missionCompletionRequested(
        new MissionCompletionRequestedEvent(10L, 3L, "책 읽기"), "자녀");

    @BeforeEach
    void createSender() {
        sender = new FcmPushSender(messaging);
    }

    @Test
    void sendsDisplayAndDataPayloadAndReturnsOnlyUnregisteredTokens() throws Exception {
        var devices = List.of(device(1), device(2), device(3));
        when(messaging.sendEach(anyList())).thenReturn(batch(List.of(success(),
            failure(MessagingErrorCode.UNREGISTERED, null), failure(MessagingErrorCode.UNAVAILABLE, null))));

        assertThat(sender.send(devices, notification)).containsExactly(devices.get(1));

        verify(messaging).sendEach(messages.capture());
        Message message = messages.getValue().get(0);
        assertThat(ReflectionTestUtils.getField(message, "token")).isEqualTo(devices.get(0).getToken());
        Notification display = (Notification) ReflectionTestUtils.getField(message, "notification");
        assertThat(ReflectionTestUtils.getField(display, "title")).isEqualTo(notification.title());
        assertThat(ReflectionTestUtils.getField(display, "body")).isEqualTo(notification.body());
        assertThat(ReflectionTestUtils.getField(message, "data")).isEqualTo(notification.data());
        assertThat(ReflectionTestUtils.getField(message, "androidConfig")).isNotNull();
        assertThat(ReflectionTestUtils.getField(message, "apnsConfig")).isNotNull();
    }

    @Test
    void invalidArgumentDeletesOnlyAnExplicitTokenError() throws Exception {
        var devices = IntStream.range(0, 5).mapToObj(this::device).toList();
        String invalidToken = """
            {"error":{"message":"The registration token is not a valid FCM registration token"}}
            """;
        String tokenField = """
            {"error":{"details":[{"fieldViolations":[{"field":"message.token"}]}]}}
            """;
        String invalidPayload = """
            {"error":{"details":[{"fieldViolations":[{"field":"message.data[0].value"}]}]}}
            """;
        when(messaging.sendEach(anyList())).thenReturn(batch(List.of(
            failure(MessagingErrorCode.INVALID_ARGUMENT, invalidToken),
            failure(MessagingErrorCode.INVALID_ARGUMENT, tokenField),
            failure(MessagingErrorCode.INVALID_ARGUMENT, invalidPayload),
            failure(MessagingErrorCode.INVALID_ARGUMENT, "malformed-dummy-response"),
            failure(MessagingErrorCode.INVALID_ARGUMENT, null))));

        assertThat(sender.send(devices, notification)).containsExactly(devices.get(0), devices.get(1));
    }

    @Test
    void splitsMoreThanFiveHundredMessagesAndContinuesAfterABatchFailure() throws Exception {
        var devices = IntStream.range(0, 501).mapToObj(this::device).toList();
        when(messaging.sendEach(anyList())).thenThrow(mock(FirebaseMessagingException.class))
            .thenReturn(batch(List.of(failure(MessagingErrorCode.UNREGISTERED, null))));

        assertThat(sender.send(devices, notification)).containsExactly(devices.get(500));

        verify(messaging, times(2)).sendEach(messages.capture());
        assertThat(messages.getAllValues()).extracting(List::size).containsExactly(500, 1);
    }

    @Test
    void credentialsProjectAndQuotaErrorsPreserveTokens() throws Exception {
        var devices = List.of(device(1), device(2), device(3));
        when(messaging.sendEach(anyList())).thenReturn(batch(List.of(
            failure(MessagingErrorCode.SENDER_ID_MISMATCH, null),
            failure(MessagingErrorCode.THIRD_PARTY_AUTH_ERROR, null),
            failure(MessagingErrorCode.QUOTA_EXCEEDED, null))));

        assertThat(sender.send(devices, notification)).isEmpty();
    }

    @Test
    void emptyRecipientListMakesNoFirebaseCall() {
        assertThat(sender.send(List.of(), notification)).isEmpty();
        verifyNoInteractions(messaging);
    }

    private DeviceToken device(int number) {
        DeviceToken device = DeviceToken.create(1L, "dummy-token-" + number, "dummy-hash-" + number, DevicePlatform.IOS);
        ReflectionTestUtils.setField(device, "id", (long) number);
        return device;
    }

    private BatchResponse batch(List<SendResponse> responses) {
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getResponses()).thenReturn(responses);
        return batch;
    }

    private SendResponse success() {
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(true);
        return response;
    }

    private SendResponse failure(MessagingErrorCode code, String body) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(code);
        if (body != null) {
            IncomingHttpResponse httpResponse = mock(IncomingHttpResponse.class);
            when(httpResponse.getContent()).thenReturn(body);
            when(exception.getHttpResponse()).thenReturn(httpResponse);
        }
        SendResponse response = mock(SendResponse.class);
        when(response.getException()).thenReturn(exception);
        return response;
    }
}
