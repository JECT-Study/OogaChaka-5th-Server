package com.ject.oogachaka.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import com.ject.oogachaka.notification.entity.DeviceToken;
import com.ject.oogachaka.notification.enums.DevicePlatform;
import com.ject.oogachaka.notification.service.PushNotification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** FCM 응답에 따라 지울 기기를 고르는지 확인한다. Firebase는 목 객체라 실제 전송은 하지 않는다. */
class FcmPushSenderTest {

    private final FirebaseMessaging messaging = mock(FirebaseMessaging.class);
    private final FcmPushSender sender = new FcmPushSender(messaging);
    private final PushNotification notification = new PushNotification("제목", "내용", Map.of("type", "TEST"));
    private final List<DeviceToken> devices = List.of(device(1), device(2), device(3));

    // UNREGISTERED만 삭제 대상으로 돌려주고, 일시 장애·설정 오류로 실패한 기기는 남긴다.
    @Test
    void 등록_해제된_기기만_돌려준다() throws Exception {
        SendResponse success = success();
        SendResponse unregistered = failure(MessagingErrorCode.UNREGISTERED);
        SendResponse unavailable = failure(MessagingErrorCode.UNAVAILABLE);
        BatchResponse response = mock(BatchResponse.class);
        when(response.getResponses()).thenReturn(List.of(success, unregistered, unavailable));
        when(messaging.sendEach(anyList())).thenReturn(response);

        assertThat(sender.send(devices, notification)).containsExactly(devices.get(1));
    }

    // 요청 전체가 실패하면 어느 토큰이 무효인지 알 수 없으므로 아무것도 지우지 않는다.
    @Test
    void 전체_전송이_실패하면_아무것도_지우지_않는다() throws Exception {
        when(messaging.sendEach(anyList())).thenThrow(mock(FirebaseMessagingException.class));

        assertThat(sender.send(devices, notification)).isEmpty();
    }

    private DeviceToken device(int number) {
        return DeviceToken.create(1L, "dummy-token-" + number, "dummy-hash-" + number, DevicePlatform.IOS);
    }

    private SendResponse success() {
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(true);
        return response;
    }

    private SendResponse failure(MessagingErrorCode code) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(code);
        SendResponse response = mock(SendResponse.class);
        when(response.getException()).thenReturn(exception);
        return response;
    }
}
