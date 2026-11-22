package com.ject.oogachaka.notification.infrastructure;

import com.ject.oogachaka.notification.entity.DeviceToken;
import com.ject.oogachaka.notification.service.PushNotification;
import com.ject.oogachaka.notification.service.PushSender;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** Firebase 자격증명과 네트워크 호출이 필요 없는 로컬 개발용 전송기 */
@Slf4j
public class NoOpPushSender implements PushSender {

    @Override
    public List<DeviceToken> send(List<DeviceToken> devices, PushNotification notification) {
        log.info("FCM 비활성화로 푸시 생략. title={}, deviceCount={}", notification.title(), devices.size());
        return List.of();
    }
}
