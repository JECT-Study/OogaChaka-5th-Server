package com.ject.oogachaka.notification.service;

import com.ject.oogachaka.mission.event.MissionCompletionRequestedEvent;
import com.ject.oogachaka.notification.repository.FamilyPushRecipientQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MissionCompletionPushListener {

    private final FamilyPushRecipientQuery recipientQuery;
    private final PushService pushService;

    // 완료 요청이 커밋된 뒤에만 보내고, 전송은 별도 스레드에서 해 요청 응답을 늦추지 않는다.
    @Async("pushExecutor")
    @TransactionalEventListener
    public void onMissionCompletionRequested(MissionCompletionRequestedEvent event) {
        recipientQuery.findParents(event.childUserId()).ifPresent(parents ->
            pushService.sendToUsers(parents.parentUserIds(), PushNotification.missionCompletionRequested(
                parents.childNickname(), event.missionId(), event.missionTitle())));
    }
}
