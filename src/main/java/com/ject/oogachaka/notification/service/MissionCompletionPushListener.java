package com.ject.oogachaka.notification.service;

import com.ject.oogachaka.mission.event.MissionCompletionRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MissionCompletionPushListener {

    private final ParentPushRecipientPort recipientPort;
    private final PushSender pushSender;
    private final DeviceTokenService deviceTokenService;

    @Async("pushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onMissionCompletionRequested(MissionCompletionRequestedEvent event) {
        try {
            recipientPort.findByChildUserId(event.childUserId()).ifPresent(recipients -> {
                if (recipients.devices().isEmpty()) {
                    return;
                }
                var notification = PushNotification.missionCompletionRequested(event, recipients.childNickname());
                var invalidDevices = pushSender.send(recipients.devices(), notification);
                if (!invalidDevices.isEmpty()) {
                    deviceTokenService.deleteInvalidRegistrations(invalidDevices);
                }
            });
        } catch (RuntimeException exception) {
            // 공급자/DB 예외 메시지, 이벤트 인자, 토큰을 Async 기본 오류 로그에 넘기지 않는다.
            log.error("미션 완료 요청 푸시 처리 실패. missionId={}, childUserId={}, errorType={}",
                event.missionId(), event.childUserId(), exception.getClass().getSimpleName());
        }
    }
}
