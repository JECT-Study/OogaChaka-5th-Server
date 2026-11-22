package com.ject.oogachaka.notification.service;

import com.ject.oogachaka.family.service.FamilyService;
import com.ject.oogachaka.mission.event.MissionCompletionRequestedEvent;
import com.ject.oogachaka.user.entity.User;
import com.ject.oogachaka.user.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** 미션 관련 푸시. 미션 이벤트마다 메서드를 하나씩 추가한다. */
@Component
@RequiredArgsConstructor
public class MissionPushListener {

    private final UserService userService;
    private final FamilyService familyService;
    private final PushService pushService;

    // 완료 요청이 커밋된 뒤에만 보내고, 전송은 별도 스레드에서 해 요청 응답을 늦추지 않는다.
    @Async("pushExecutor")
    @TransactionalEventListener
    public void onMissionCompletionRequested(MissionCompletionRequestedEvent event) {
        User child = userService.getUser(event.childUserId());
        List<Long> parentUserIds = familyService.listParentUserIds(child.getFamilyMemberId());

        if (parentUserIds.isEmpty()) {
            return;
        }

        pushService.sendToUsers(parentUserIds, PushNotification.missionCompletionRequested(
            child.getNickname(), event.missionId(), event.missionTitle()));
    }
}
