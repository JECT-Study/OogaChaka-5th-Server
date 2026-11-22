package com.ject.oogachaka.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.notification.entity.DeviceToken;
import com.ject.oogachaka.notification.enums.DevicePlatform;
import com.ject.oogachaka.notification.repository.DeviceTokenRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 발송 대상 조회와 무효 토큰 정리 흐름만 확인한다. 전송기와 DB는 목 객체다. */
@ExtendWith(MockitoExtension.class)
class PushServiceTest {

    @Mock
    private DeviceTokenRepository repository;

    @Mock
    private PushSender pushSender;

    @InjectMocks
    private PushService service;

    private final PushNotification notification = new PushNotification("제목", "내용", Map.of());

    // 등록 기기가 없으면 외부 전송을 호출하지 않는다.
    @Test
    void 기기가_없으면_보내지_않는다() {
        when(repository.findAllByUserIdIn(List.of(1L))).thenReturn(List.of());

        service.sendToUsers(List.of(1L), notification);

        verifyNoInteractions(pushSender);
    }

    // 전송기가 등록 해제됐다고 돌려준 기기만 삭제한다.
    @Test
    void 등록_해제된_기기만_삭제한다() {
        DeviceToken valid = DeviceToken.create(1L, "token-1", "hash-1", DevicePlatform.IOS);
        DeviceToken unregistered = DeviceToken.create(1L, "token-2", "hash-2", DevicePlatform.ANDROID);
        when(repository.findAllByUserIdIn(List.of(1L))).thenReturn(List.of(valid, unregistered));
        when(pushSender.send(List.of(valid, unregistered), notification)).thenReturn(List.of(unregistered));

        service.sendToUsers(List.of(1L), notification);

        verify(repository).deleteAllInBatch(List.of(unregistered));
    }

    // 모두 성공하면 삭제하지 않는다.
    @Test
    void 모두_성공하면_삭제하지_않는다() {
        DeviceToken valid = DeviceToken.create(1L, "token-1", "hash-1", DevicePlatform.IOS);
        when(repository.findAllByUserIdIn(List.of(1L))).thenReturn(List.of(valid));
        when(pushSender.send(List.of(valid), notification)).thenReturn(List.of());

        service.sendToUsers(List.of(1L), notification);

        verify(repository, never()).deleteAllInBatch(any());
    }
}
