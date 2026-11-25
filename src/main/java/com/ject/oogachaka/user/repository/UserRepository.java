package com.ject.oogachaka.user.repository;

import com.ject.oogachaka.user.entity.User;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findAllByFamilyMemberIdIn(Collection<Long> familyMemberIds);
}
