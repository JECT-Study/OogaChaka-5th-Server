package com.ject.oogachaka.notification.service;

import com.ject.oogachaka.mission.event.MissionCompletionRequestedEvent;
import com.ject.oogachaka.notification.enums.NotificationType;
import java.util.Map;

public record PushNotification(NotificationType type, String title, String body, Map<String, String> data) {

    private static final int MAX_MISSION_TITLE_LENGTH = 64;

    public PushNotification {
        data = Map.copyOf(data);
    }

    public static PushNotification missionCompletionRequested(MissionCompletionRequestedEvent event, String childNickname) {
        return new PushNotification(NotificationType.MISSION_COMPLETION_REQUESTED,
            "미션 완료 인증 요청", childNickname + "님이 '" + formatMissionTitle(event.missionTitle()) + "' 완료 인증을 요청했어요",
            Map.of("type", NotificationType.MISSION_COMPLETION_REQUESTED.name(),
                "missionId", event.missionId().toString(), "childUserId", event.childUserId().toString()));
    }

    private static String formatMissionTitle(String missionTitle) {
        if (missionTitle.isBlank()) {
            return "미션";
        }
        if (missionTitle.codePointCount(0, missionTitle.length()) > MAX_MISSION_TITLE_LENGTH) {
            // 보조 평면 문자도 중간에서 끊지 않고 생략 기호까지 64자로 제한한다.
            int end = missionTitle.offsetByCodePoints(0, MAX_MISSION_TITLE_LENGTH - 1);
            return missionTitle.substring(0, end) + "…";
        }
        return missionTitle;
    }

    @Override
    public String toString() {
        return "PushNotification[type=" + type + "]";
    }
}
