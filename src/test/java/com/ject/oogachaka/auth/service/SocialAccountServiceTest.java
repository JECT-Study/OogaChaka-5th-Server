package com.ject.oogachaka.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.auth.repository.SocialAccountRepository;
import com.ject.oogachaka.user.entity.User;
import com.ject.oogachaka.user.service.UserService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 저장 전 소셜 프로필 보정 규칙을 검증한다. 사용자·계정 저장은 목 객체로 대체하며 실제 DB 저장은 확인하지 않는다. */
class SocialAccountServiceTest {

    private static final String HASH = "a".repeat(64);
    private final SocialAccountRepository repository = mock(SocialAccountRepository.class);
    private final UserService users = mock(UserService.class);
    private final SocialAccountService service = new SocialAccountService(repository, users);

    // 이모지 31개로 된 닉네임은 글자 중간이 깨지지 않게 유니코드 코드 포인트 기준 30개로 잘라 사용자 생성에 전달한다.
    @Test
    void nicknameIsTruncatedWithoutSplittingSurrogatePairs() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(users.createUser(any())).thenReturn(user);
        when(repository.findByAuthProviderAndProviderUserIdHash(AuthProvider.GOOGLE, HASH)).thenReturn(Optional.empty());
        String nickname = "😀".repeat(31);
        service.findOrCreate(AuthProvider.GOOGLE, HASH, nickname, "test@example.invalid");
        verify(users).createUser("😀".repeat(30));
    }

    // 제공자가 닉네임을 주지 않거나 공백이면 우가차차_임의8자리 기본 닉네임으로 사용자를 생성한다.
    @Test
    void missingNicknameGetsDefault() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(users.createUser(any())).thenReturn(user);
        when(repository.findByAuthProviderAndProviderUserIdHash(AuthProvider.KAKAO, HASH)).thenReturn(Optional.empty());
        service.findOrCreate(AuthProvider.KAKAO, HASH, " ", null);
        ArgumentCaptor<String> nickname = ArgumentCaptor.forClass(String.class);
        verify(users).createUser(nickname.capture());
        assertThat(nickname.getValue()).matches("우가차차_[0-9a-f]{8}");
    }
}
