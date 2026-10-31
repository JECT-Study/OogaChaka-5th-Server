package com.ject.oogachaka.notification.service;

import com.ject.oogachaka.notification.entity.DeviceToken;
import java.util.List;

public record ParentPushRecipients(String childNickname, List<DeviceToken> devices) {

    public ParentPushRecipients {
        devices = List.copyOf(devices);
    }

    @Override
    public String toString() {
        return "ParentPushRecipients[deviceCount=" + devices.size() + "]";
    }
}
