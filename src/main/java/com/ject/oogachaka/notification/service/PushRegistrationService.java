package com.ject.oogachaka.notification.service;

import com.ject.oogachaka.global.crypto.HmacPurpose;
import com.ject.oogachaka.global.crypto.HmacService;
import com.ject.oogachaka.notification.dto.PushRegistrationRequest;
import com.ject.oogachaka.notification.dto.PushRegistrationDeleteRequest;
import com.ject.oogachaka.notification.entity.DeviceToken;
import com.ject.oogachaka.notification.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushRegistrationService {

    private final HmacService hmacService;
    private final DeviceTokenRepository deviceTokenRepository;

    // 이미 등록된 토큰이면 소유자·플랫폼만 갱신해 같은 기기가 여러 행으로 늘어나지 않게 한다.
    @Transactional
    public void register(Long userId, PushRegistrationRequest request) {
        String tokenHash = hmacService.hash(request.token(), HmacPurpose.DEVICE_TOKEN);
        deviceTokenRepository.findByTokenHash(tokenHash).ifPresentOrElse(
            device -> device.synchronize(userId, request.devicePlatform()),
            () -> deviceTokenRepository.save(
                DeviceToken.create(userId, request.token(), tokenHash, request.devicePlatform())));
    }

    // 본인 소유 토큰만 지워, 계정 전환 전의 세션이 새 소유자의 등록을 해제하지 못하게 한다.
    @Transactional
    public void delete(Long userId, PushRegistrationDeleteRequest request) {
        deviceTokenRepository.deleteByUserIdAndTokenHash(userId,
            hmacService.hash(request.token(), HmacPurpose.DEVICE_TOKEN));
    }
}
