package com.ject.oogachaka.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.global.crypto.HmacPurpose;
import com.ject.oogachaka.global.crypto.HmacService;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import com.ject.oogachaka.notification.dto.PushRegistrationRequest;
import com.ject.oogachaka.notification.dto.PushUnregistrationRequest;
import com.ject.oogachaka.notification.enums.DevicePlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PushRegistrationServiceTest {

    @Mock
    private HmacService hmacService;

    @Mock
    private DeviceTokenService deviceTokenService;

    @InjectMocks
    private PushRegistrationService service;

    @Test
    void registrationUsesTheOriginalStringAndDeviceTokenHmacKey() {
        var request = new PushRegistrationRequest("CaseSensitive-Dummy-Token", DevicePlatform.IOS);
        when(hmacService.hash(request.token(), HmacPurpose.DEVICE_TOKEN)).thenReturn("hash");

        service.register(1L, request);

        verify(deviceTokenService).upsert(1L, request.token(), "hash", DevicePlatform.IOS);
    }

    @Test
    void concurrentInsertOrGapLockFailureRetriesThroughTheTransactionBoundary() {
        var request = new PushRegistrationRequest("dummy-token", DevicePlatform.ANDROID);
        when(hmacService.hash(request.token(), HmacPurpose.DEVICE_TOKEN)).thenReturn("hash");
        doThrow(new DataIntegrityViolationException("dummy-unique-conflict"))
            .doThrow(new CannotAcquireLockException("dummy-gap-lock"))
            .doNothing().when(deviceTokenService).upsert(1L, request.token(), "hash", DevicePlatform.ANDROID);

        service.register(1L, request);

        verify(deviceTokenService, times(3)).upsert(1L, request.token(), "hash", DevicePlatform.ANDROID);
        verify(hmacService).hash(request.token(), HmacPurpose.DEVICE_TOKEN);
    }

    @Test
    void contentionHasABoundedRetryCountAndASafeBusinessError() {
        var request = new PushRegistrationRequest("dummy-token", DevicePlatform.WEB);
        when(hmacService.hash(request.token(), HmacPurpose.DEVICE_TOKEN)).thenReturn("hash");
        doThrow(new CannotAcquireLockException("dummy-lock-contention"))
            .when(deviceTokenService).upsert(1L, request.token(), "hash", DevicePlatform.WEB);

        assertThatThrownBy(() -> service.register(1L, request))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode())
                    .isEqualTo(ErrorCode.PUSH_REGISTRATION_CONFLICT));
        verify(deviceTokenService, times(3)).upsert(1L, request.token(), "hash", DevicePlatform.WEB);
    }

    @Test
    void unauthenticatedRegistrationAndUnregistrationAreRejectedBeforeHashing() {
        assertThatThrownBy(() -> service.register(null, new PushRegistrationRequest("dummy", DevicePlatform.IOS)))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.unregister(null, new PushUnregistrationRequest("dummy")))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(hmacService, deviceTokenService);
    }

    @Test
    void unregisterHashesTheTokenAndPassesTheAuthenticatedOwner() {
        when(hmacService.hash("dummy-token", HmacPurpose.DEVICE_TOKEN)).thenReturn("hash");

        service.unregister(1L, new PushUnregistrationRequest("dummy-token"));

        verify(deviceTokenService).unregister(1L, "hash");
    }
}
