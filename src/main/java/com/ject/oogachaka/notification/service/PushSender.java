package com.ject.oogachaka.notification.service;

import com.ject.oogachaka.notification.entity.DeviceToken;
import java.util.List;

/** 외부 전송을 수행하고, 영구적으로 무효한 토큰의 스냅샷을 반환한다. */
public interface PushSender {

    List<DeviceToken> send(List<DeviceToken> devices, PushNotification notification);
}
