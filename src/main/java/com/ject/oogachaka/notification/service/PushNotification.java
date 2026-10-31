package com.ject.oogachaka.notification.service;

import com.ject.oogachaka.mission.event.MissionCompletionRequestedEvent;
import com.ject.oogachaka.notification.enums.NotificationType;
import java.util.Map;

public record PushNotification(NotificationType type, String title, String body, Map<String, String> data) {

    public PushNotification {
        data = Map.copyOf(data);
    }

    public static PushNotification missionCompletionRequested(MissionCompletionRequestedEvent event, String childNickname) {
        return new PushNotification(NotificationType.MISSION_COMPLETION_REQUESTED,
            "미션 완료 인증 요청", childNickname + "님이 '" + event.missionTitle() + "' 완료 인증을 요청했어요",
            Map.of("type", NotificationType.MISSION_COMPLETION_REQUESTED.name(),
                "missionId", event.missionId().toString(), "childUserId", event.childUserId().toString()));
    }

    @Override
    public String toString() {
        return "PushNotification[type=" + type + "]";
    }
}
