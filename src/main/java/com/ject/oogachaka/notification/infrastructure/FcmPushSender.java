package com.ject.oogachaka.notification.infrastructure;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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

    private static final int BATCH_SIZE = 500;
    private static final String INVALID_REGISTRATION_MESSAGE =
        "The registration token is not a valid FCM registration token";

    private final FirebaseMessaging messaging;

    @Override
    public List<DeviceToken> send(List<DeviceToken> devices, PushNotification notification) {
        List<DeviceToken> invalidDevices = new ArrayList<>();
        for (int offset = 0; offset < devices.size(); offset += BATCH_SIZE) {
            List<DeviceToken> batch = devices.subList(offset, Math.min(offset + BATCH_SIZE, devices.size()));
            try {
                BatchResponse response = messaging.sendEach(batch.stream()
                    .map(device -> createMessage(device, notification)).toList());
                for (int index = 0; index < batch.size(); index++) {
                    var result = response.getResponses().get(index);
                    if (!result.isSuccessful()) {
                        var exception = result.getException();
                        DeviceToken device = batch.get(index);
                        if (isInvalidToken(exception)) {
                            invalidDevices.add(device);
                        }
                        log.warn("FCM 개별 전송 실패. deviceId={}, errorCode={}", device.getId(),
                            exception == null ? "UNKNOWN" : exception.getMessagingErrorCode());
                    }
                }
            } catch (FirebaseMessagingException | RuntimeException exception) {
                // 배치 전체 실패는 특정 토큰의 무효 여부를 보장하지 않는다.
                log.warn("FCM 배치 전송 실패. type={}, deviceCount={}, errorType={}",
                    notification.type(), batch.size(), exception.getClass().getSimpleName());
            }
        }
        return List.copyOf(invalidDevices);
    }

    // 9.11.0은 FID 방식도 제공하지만 현재 API 계약은 클라이언트가 등록한 FCM registration token이다.
    @SuppressWarnings("deprecation")
    private Message createMessage(DeviceToken device, PushNotification notification) {
        return Message.builder()
            .setToken(device.getToken())
            .setNotification(Notification.builder().setTitle(notification.title()).setBody(notification.body()).build())
            .putAllData(notification.data())
            .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
            .setApnsConfig(ApnsConfig.builder().putHeader("apns-push-type", "alert")
                .putHeader("apns-priority", "10").setAps(Aps.builder().setSound("default").build()).build())
            .build();
    }

    private boolean isInvalidToken(FirebaseMessagingException exception) {
        if (exception == null || exception.getMessagingErrorCode() == null) {
            return false;
        }
        return switch (exception.getMessagingErrorCode()) {
            case UNREGISTERED -> true;
            case INVALID_ARGUMENT -> hasInvalidTokenDetail(exception);
            default -> false;
        };
    }

    private boolean hasInvalidTokenDetail(FirebaseMessagingException exception) {
        // INVALID_ARGUMENT에는 잘못된 payload도 포함된다. 토큰 오류라고 확인된 경우만 삭제한다.
        if (exception.getHttpResponse() == null || exception.getHttpResponse().getContent() == null) {
            return false;
        }
        try {
            var error = JsonParser.parseString(exception.getHttpResponse().getContent())
                .getAsJsonObject().getAsJsonObject("error");
            if (error == null) {
                return false;
            }
            if (error.has("message") && INVALID_REGISTRATION_MESSAGE.equals(error.get("message").getAsString())) {
                return true;
            }
            if (error.has("details")) {
                for (JsonElement detail : error.getAsJsonArray("details")) {
                    var object = detail.getAsJsonObject();
                    if (object.has("fieldViolations")) {
                        for (JsonElement violation : object.getAsJsonArray("fieldViolations")) {
                            var field = violation.getAsJsonObject().get("field");
                            if (field != null && "message.token".equals(field.getAsString())) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException exceptionWithSensitiveContent) {
            // 응답 원문/파싱 오류는 로깅하지 않고 불확실한 토큰을 보존한다.
            return false;
        }
        return false;
    }
}
