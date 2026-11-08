package com.ject.oogachaka.notification.service;

import com.ject.oogachaka.notification.enums.NotificationType;
import java.util.Map;

// 알림 문구와 앱이 화면 이동에 쓸 data. 새 알림은 정적 메서드를 추가한다.
public record PushNotification(String title, String body, Map<String, String> data) {

    public static PushNotification missionCompletionRequested(String childNickname, Long missionId, String missionTitle) {
        return new PushNotification("미션 완료 인증 요청",
            childNickname + "님이 '" + missionTitle + "' 완료 인증을 요청했어요",
            Map.of("type", NotificationType.MISSION_COMPLETION_REQUESTED.name(), "missionId", missionId.toString()));
    }
}
