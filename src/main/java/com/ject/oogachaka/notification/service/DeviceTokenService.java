package com.ject.oogachaka.notification.service;

import com.ject.oogachaka.notification.entity.DeviceToken;
import com.ject.oogachaka.notification.enums.DevicePlatform;
import com.ject.oogachaka.notification.repository.DeviceTokenRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 재시도마다 독립된 트랜잭션을 열어 UNIQUE 충돌로 rollback-only가 된 트랜잭션을 재사용하지 않는다. */
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsert(Long userId, String token, String tokenHash, DevicePlatform devicePlatform) {
        repository.findByTokenHashForUpdate(tokenHash).ifPresentOrElse(
            device -> device.synchronize(userId, devicePlatform),
            () -> repository.saveAndFlush(DeviceToken.create(userId, token, tokenHash, devicePlatform)));
    }

    @Transactional
    public void unregister(Long userId, String tokenHash) {
        repository.deleteRegistration(userId, tokenHash);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteInvalidRegistrations(List<DeviceToken> devices) {
        for (DeviceToken device : devices) {
            repository.deleteInvalidRegistration(device.getId(), device.getUserId(), device.getLastSyncedAt());
        }
    }
}
