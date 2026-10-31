package com.ject.oogachaka.notification.repository;

import com.ject.oogachaka.notification.entity.DeviceToken;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT device FROM DeviceToken device WHERE device.tokenHash = :tokenHash")
    Optional<DeviceToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<DeviceToken> findAllByUserIdIn(Collection<Long> userIds);

    @Modifying
    @Query("DELETE FROM DeviceToken device WHERE device.userId = :userId AND device.tokenHash = :tokenHash")
    int deleteRegistration(@Param("userId") Long userId, @Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
        DELETE FROM DeviceToken device
        WHERE device.id = :id AND device.userId = :userId AND device.lastSyncedAt = :lastSyncedAt
        """)
    int deleteInvalidRegistration(@Param("id") Long id, @Param("userId") Long userId,
        @Param("lastSyncedAt") LocalDateTime lastSyncedAt);
}
