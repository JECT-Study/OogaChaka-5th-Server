package com.ject.oogachaka.notification.infrastructure;

import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.ject.oogachaka.notification.entity.DeviceToken;
import com.ject.oogachaka.notification.service.PushNotification;
import com.ject.oogachaka.notification.service.PushSender;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class FcmPushSender implements PushSender {

    private final FirebaseMessaging messaging;

    @Override
    public List<DeviceToken> send(List<DeviceToken> devices, PushNotification notification) {
        List<SendResponse> responses;
        try {
            responses = messaging.sendEach(devices.stream().map(device -> createMessage(device, notification)).toList())
                .getResponses();
        } catch (FirebaseMessagingException exception) {
            log.warn("FCM 전송 실패. deviceCount={}, errorCode={}", devices.size(), exception.getMessagingErrorCode());
            return List.of();
        }

        List<DeviceToken> unregisteredDevices = new ArrayList<>();
        for (int index = 0; index < devices.size(); index++) {
            SendResponse response = responses.get(index);
            if (response.isSuccessful()) {
                continue;
            }
            MessagingErrorCode errorCode = response.getException().getMessagingErrorCode();
            // 일시 장애나 설정 오류로 실패한 토큰은 지우지 않는다.
            if (errorCode == MessagingErrorCode.UNREGISTERED) {
                unregisteredDevices.add(devices.get(index));
            } else {
                log.warn("FCM 개별 전송 실패. deviceId={}, errorCode={}", devices.get(index).getId(), errorCode);
            }
        }
        return unregisteredDevices;
    }

    // iOS는 sound를 지정해야 알림음이 난다.
    @SuppressWarnings("deprecation")
    private Message createMessage(DeviceToken device, PushNotification notification) {
        return Message.builder()
            .setToken(device.getToken())
            .setNotification(Notification.builder().setTitle(notification.title()).setBody(notification.body()).build())
            .putAllData(notification.data())
            .setApnsConfig(ApnsConfig.builder().setAps(Aps.builder().setSound("default").build()).build())
            .build();
    }
}
