package com.ject.oogachaka.family.repository;

import com.ject.oogachaka.family.entity.Family;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface FamilyRepository extends JpaRepository<Family, Long> {

    Optional<Family> findByInviteCode(String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Family> findForChildJoinByInviteCode(String inviteCode);
}
