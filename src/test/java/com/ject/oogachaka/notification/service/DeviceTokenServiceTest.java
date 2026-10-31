package com.ject.oogachaka.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.notification.entity.DeviceToken;
import com.ject.oogachaka.notification.enums.DevicePlatform;
import com.ject.oogachaka.notification.repository.DeviceTokenRepository;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock
    private DeviceTokenRepository repository;

    @InjectMocks
    private DeviceTokenService service;

    @Test
    void createsNewTokenWithExplicitHashAndPlatform() {
        when(repository.findByTokenHashForUpdate("hash")).thenReturn(Optional.empty());

        service.upsert(1L, "dummy-fcm-token", "hash", DevicePlatform.ANDROID);

        var saved = ArgumentCaptor.forClass(DeviceToken.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(1L);
        assertThat(saved.getValue().getToken()).isEqualTo("dummy-fcm-token");
        assertThat(saved.getValue().getTokenHash()).isEqualTo("hash");
        assertThat(saved.getValue().getDevicePlatform()).isEqualTo(DevicePlatform.ANDROID);
        assertThat(saved.getValue().getLastSyncedAt()).isEqualTo(
            saved.getValue().getLastSyncedAt().truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void repeatedRegistrationUpdatesExistingRowWithoutInserting() {
        DeviceToken existing = DeviceToken.create(1L, "dummy-fcm-token", "hash", DevicePlatform.ANDROID);
        var previousSync = existing.getLastSyncedAt();
        when(repository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(existing));

        service.upsert(1L, "dummy-fcm-token", "hash", DevicePlatform.IOS);

        assertThat(existing.getUserId()).isEqualTo(1L);
        assertThat(existing.getDevicePlatform()).isEqualTo(DevicePlatform.IOS);
        assertThat(existing.getLastSyncedAt()).isAfter(previousSync);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void tokenMovesToTheCurrentlyAuthenticatedUser() {
        DeviceToken existing = DeviceToken.create(1L, "dummy-fcm-token", "hash", DevicePlatform.ANDROID);
        when(repository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(existing));

        service.upsert(2L, "dummy-fcm-token", "hash", DevicePlatform.WEB);

        assertThat(existing.getUserId()).isEqualTo(2L);
        assertThat(existing.getDevicePlatform()).isEqualTo(DevicePlatform.WEB);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void deletionIsRestrictedToTheSnapshotThatActuallyFailed() {
        DeviceToken device = DeviceToken.create(2L, "dummy-fcm-token", "hash", DevicePlatform.WEB);
        ReflectionTestUtils.setField(device, "id", 7L);

        service.deleteInvalidRegistrations(List.of(device));

        verify(repository).deleteInvalidRegistration(7L, 2L, device.getLastSyncedAt());
    }

    @Test
    void unregisterIncludesTheOwnerSoAnOldSessionCannotDeleteTransferredTokens() {
        service.unregister(1L, "hash");

        verify(repository).deleteRegistration(1L, "hash");
    }
}
