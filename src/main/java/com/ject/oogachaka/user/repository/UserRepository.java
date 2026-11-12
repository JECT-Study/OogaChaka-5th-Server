package com.ject.oogachaka.user.repository;

import com.ject.oogachaka.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
