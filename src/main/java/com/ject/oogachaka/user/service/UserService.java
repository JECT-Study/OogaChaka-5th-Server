package com.ject.oogachaka.user.service;

import com.ject.oogachaka.user.entity.User;
import com.ject.oogachaka.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// [검토] 다른 계층에서 생성자 주입으로 사용할 업무 처리 객체를 Spring 빈으로 등록한다.
@Service
// [검토] final 의존성을 받는 생성자를 Lombok으로 생성해 Spring이 필요한 객체를 주입하게 한다.
@RequiredArgsConstructor
// [검토] DB 서비스에 조회 전용 기본값을 두는 팀 규칙이다. 사용자 생성은 아래 쓰기 트랜잭션 설정으로 재정의한다.
@Transactional(readOnly = true)
// [검토] 사용자 도메인의 저장 경계다. 인증 서비스가 사용자 Repository를 직접 호출하지 않게 한다.
public class UserService {

    // [검토] 사용자 엔티티를 저장하고 IDENTITY로 생성된 사용자 ID를 받기 위한 JPA 저장소다.
    private final UserRepository userRepository;

    /** 소셜 계정과 같은 트랜잭션에서만 사용자를 생성한다. */
    // [검토] 기존 쓰기 트랜잭션에 반드시 참여시킨다. 사용자만 따로 커밋되어 소셜 계정과 분리되는 호출은 거부한다.
    @Transactional(propagation = Propagation.MANDATORY)
    // [검토] 현재 호출자는 가입 트랜잭션의 SocialAccountService다. 독립 회원가입 API로 범용화한 메서드는 아니다.
    public User createUser(String nickname) {
        // [검토] 엔티티 팩토리의 기본 포인트·가족 미가입 상태를 사용해 저장한다. 커밋·롤백은 바깥 가입 트랜잭션이 결정한다.
        return userRepository.save(User.create(nickname));
    }
}
