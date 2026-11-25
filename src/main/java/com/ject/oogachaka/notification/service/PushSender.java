package com.ject.oogachaka.notification.service;

import com.ject.oogachaka.notification.entity.DeviceToken;
import java.util.List;

/** 푸시를 보내고, 등록이 해제되어 더 이상 받을 수 없는 기기를 돌려준다. */
public interface PushSender {

    List<DeviceToken> send(List<DeviceToken> devices, PushNotification notification);
}
