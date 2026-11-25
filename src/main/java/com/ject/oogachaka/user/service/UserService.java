package com.ject.oogachaka.user.service;

import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import com.ject.oogachaka.user.entity.User;
import com.ject.oogachaka.user.repository.UserRepository;
import java.util.Collection;
import java.util.List;
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

    public User getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    public List<Long> listUserIdsByFamilyMemberIds(Collection<Long> familyMemberIds) {
        return userRepository.findAllByFamilyMemberIdIn(familyMemberIds).stream().map(User::getId).toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void joinFamily(Long userId, Long familyMemberId) {
        getUser(userId).joinFamily(familyMemberId);
    }

    public List<User> listUsersByFamilyMemberIds(Collection<Long> familyMemberIds) {
        return userRepository.findAllByFamilyMemberIdIn(familyMemberIds);
    }

    /** 가족 생성과 같은 트랜잭션에서만 부모가 정한 프로필로 자녀 사용자를 만든다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public User createChildUser(String nickname, String profileImageUrl) {
        return userRepository.save(User.createChild(nickname, profileImageUrl));
    }
}
