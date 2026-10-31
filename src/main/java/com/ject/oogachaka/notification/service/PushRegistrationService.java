package com.ject.oogachaka.notification.service;

import com.ject.oogachaka.global.crypto.HmacPurpose;
import com.ject.oogachaka.global.crypto.HmacService;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import com.ject.oogachaka.notification.dto.PushRegistrationRequest;
import com.ject.oogachaka.notification.dto.PushUnregistrationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PushRegistrationService {

    private static final int MAX_ATTEMPTS = 3;

    private final HmacService hmacService;
    private final DeviceTokenService deviceTokenService;

    public void register(Long userId, PushRegistrationRequest request) {
        requireUser(userId);
        String tokenHash = hmacService.hash(request.token(), HmacPurpose.DEVICE_TOKEN);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                deviceTokenService.upsert(userId, request.token(), tokenHash, request.devicePlatform());
                return;
            } catch (DataIntegrityViolationException | PessimisticLockingFailureException exception) {
                // 없는 토큰의 동시 INSERT/갭 잠금 교착은 새 트랜잭션에서 기존 행 조회로 수렴한다.
                if (attempt == MAX_ATTEMPTS) {
                    throw new BusinessException(ErrorCode.PUSH_REGISTRATION_CONFLICT);
                }
            }
        }
    }

    public void unregister(Long userId, PushUnregistrationRequest request) {
        requireUser(userId);
        deviceTokenService.unregister(userId, hmacService.hash(request.token(), HmacPurpose.DEVICE_TOKEN));
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
