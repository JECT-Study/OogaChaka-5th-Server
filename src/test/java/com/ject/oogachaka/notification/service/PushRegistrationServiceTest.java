package com.ject.oogachaka.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.global.crypto.HmacPurpose;
import com.ject.oogachaka.global.crypto.HmacService;
import com.ject.oogachaka.notification.dto.PushRegistrationRequest;
import com.ject.oogachaka.notification.dto.PushRegistrationDeleteRequest;
import com.ject.oogachaka.notification.entity.DeviceToken;
import com.ject.oogachaka.notification.enums.DevicePlatform;
import com.ject.oogachaka.notification.repository.DeviceTokenRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 등록·해제 분기만 확인한다. DB는 목 객체라 UNIQUE 제약 자체는 검증하지 않는다. */
@ExtendWith(MockitoExtension.class)
class PushRegistrationServiceTest {

    @Mock
    private HmacService hmacService;

    @Mock
    private DeviceTokenRepository repository;

    @InjectMocks
    private PushRegistrationService service;

    @BeforeEach
    void setUp() {
        when(hmacService.hash("dummy-token", HmacPurpose.DEVICE_TOKEN)).thenReturn("hash");
    }

    // 처음 보는 토큰이면 해시와 함께 새 기기로 저장한다.
    @Test
    void 새_토큰이면_저장한다() {
        when(repository.findByTokenHash("hash")).thenReturn(Optional.empty());

        service.register(7L, new PushRegistrationRequest("dummy-token", DevicePlatform.IOS));

        ArgumentCaptor<DeviceToken> saved = ArgumentCaptor.forClass(DeviceToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(7L);
        assertThat(saved.getValue().getTokenHash()).isEqualTo("hash");
    }

    // 같은 기기에서 다른 계정으로 로그인하면 새 행을 만들지 않고 소유자를 바꾼다.
    @Test
    void 이미_등록된_토큰이면_소유자를_바꾼다() {
        DeviceToken device = DeviceToken.create(3L, "dummy-token", "hash", DevicePlatform.ANDROID);
        when(repository.findByTokenHash("hash")).thenReturn(Optional.of(device));

        service.register(7L, new PushRegistrationRequest("dummy-token", DevicePlatform.IOS));

        assertThat(device.getUserId()).isEqualTo(7L);
        assertThat(device.getDevicePlatform()).isEqualTo(DevicePlatform.IOS);
        verify(repository, never()).save(any());
    }

    // 해제는 요청한 사용자의 토큰만 지운다.
    @Test
    void 해제는_본인_토큰만_지운다() {
        service.delete(7L, new PushRegistrationDeleteRequest("dummy-token"));

        verify(repository).deleteByUserIdAndTokenHash(7L, "hash");
    }
}
