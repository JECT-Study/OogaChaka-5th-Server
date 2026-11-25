package com.ject.oogachaka.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.family.service.FamilyService;
import com.ject.oogachaka.mission.event.MissionCompletionRequestedEvent;
import com.ject.oogachaka.user.entity.User;
import com.ject.oogachaka.user.service.UserService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 수신자와 알림 문구를 만드는 부분만 확인한다. 커밋 후·별도 스레드 실행은 MissionPushTransactionTest에서 본다. */
@ExtendWith(MockitoExtension.class)
class MissionPushListenerTest {

    @Mock
    private UserService userService;

    @Mock
    private FamilyService familyService;

    @Mock
    private PushService pushService;

    @InjectMocks
    private MissionPushListener listener;

    private final MissionCompletionRequestedEvent event = new MissionCompletionRequestedEvent(10L, 3L, "책 읽기");

    // 자녀의 가족 부모 전원에게 자녀 닉네임과 미션 제목이 담긴 알림을 보낸다.
    @Test
    void 가족_부모_전원에게_보낸다() {
        when(userService.getUser(3L)).thenReturn(child(20L));
        when(familyService.listParentUserIds(20L)).thenReturn(List.of(1L, 2L));

        listener.onMissionCompletionRequested(event);

        ArgumentCaptor<PushNotification> notification = ArgumentCaptor.forClass(PushNotification.class);
        verify(pushService).sendToUsers(eq(List.of(1L, 2L)), notification.capture());
        assertThat(notification.getValue().body()).isEqualTo("하늘님이 '책 읽기' 완료 인증을 요청했어요");
        assertThat(notification.getValue().data())
            .isEqualTo(Map.of("type", "MISSION_COMPLETION_REQUESTED", "missionId", "10"));
    }

    // 가족이나 부모가 없으면 보내지 않는다.
    @Test
    void 부모가_없으면_보내지_않는다() {
        when(userService.getUser(3L)).thenReturn(child(null));
        when(familyService.listParentUserIds(null)).thenReturn(List.of());

        listener.onMissionCompletionRequested(event);

        verifyNoInteractions(pushService);
    }

    private User child(Long familyMemberId) {
        User child = User.create("하늘");
        ReflectionTestUtils.setField(child, "familyMemberId", familyMemberId);
        return child;
    }
}
