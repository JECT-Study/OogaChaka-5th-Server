package com.ject.oogachaka.notification.repository;

import com.ject.oogachaka.notification.entity.DeviceToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByTokenHash(String tokenHash);

    List<DeviceToken> findAllByUserIdIn(Collection<Long> userIds);

    void deleteByUserIdAndTokenHash(Long userId, String tokenHash);
}
