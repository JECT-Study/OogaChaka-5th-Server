package com.ject.oogachaka.user.repository;

import com.ject.oogachaka.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findAllByFamilyMemberIdIn(Collection<Long> familyMemberIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findForFamilyJoinById(Long id);
}
