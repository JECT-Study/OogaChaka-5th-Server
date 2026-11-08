package com.ject.oogachaka.user.service;

import com.ject.oogachaka.user.entity.User;
import com.ject.oogachaka.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /** 소셜 계정과 같은 트랜잭션에서만 사용자를 생성한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public User createUser(String nickname) {
        return userRepository.save(User.create(nickname));
    }
}
