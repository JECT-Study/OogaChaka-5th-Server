package com.ject.oogachaka.notification.service;

import com.ject.oogachaka.notification.entity.DeviceToken;
import com.ject.oogachaka.notification.repository.DeviceTokenRepository;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 사용자들의 등록 기기로 푸시를 보낸다. 받는 사람이 부모든 자녀든 이 메서드를 쓴다.
 * 외부 전송을 DB 트랜잭션으로 감싸지 않도록 클래스 트랜잭션을 두지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PushService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final PushSender pushSender;

    public void sendToUsers(Collection<Long> userIds, PushNotification notification) {
        List<DeviceToken> devices = deviceTokenRepository.findAllByUserIdIn(userIds);
        if (devices.isEmpty()) {
            return;
        }

        // 앱 삭제 등으로 등록이 해제된 토큰은 다음 발송에서 빠지도록 지운다.
        List<DeviceToken> unregisteredDevices = pushSender.send(devices, notification);
        if (!unregisteredDevices.isEmpty()) {
            deviceTokenRepository.deleteAllInBatch(unregisteredDevices);
        }
    }
}
