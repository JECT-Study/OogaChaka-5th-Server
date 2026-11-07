package com.ject.oogachaka.user.repository;

import com.ject.oogachaka.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

// [검토] UserService가 기본 JPA 저장을 사용할 저장소다. 인증 전용 사용자 조회를 위해 추가 쿼리나 별도 구현 계층은 만들지 않았다.
public interface UserRepository extends JpaRepository<User, Long> {
}
