package com.ject.oogachaka.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.auth.entity.SocialAccount;
import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.auth.repository.SocialAccountRepository;
import com.ject.oogachaka.user.entity.User;
import com.ject.oogachaka.user.service.UserService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SocialAccountServiceTest {

    private static final String HASH = "a".repeat(64);
    private final SocialAccountRepository repository = mock(SocialAccountRepository.class);
    private final UserService users = mock(UserService.class);
    private final SocialAccountService service = new SocialAccountService(repository, users);

    @Test
    void existingAccountDoesNotCreateUserOrStoreProviderFieldsAgain() {
        when(repository.findByAuthProviderAndProviderUserIdHash(AuthProvider.GOOGLE, HASH))
            .thenReturn(Optional.of(SocialAccount.create(42L, AuthProvider.GOOGLE, HASH, null)));
        assertThat(service.findOrCreate(AuthProvider.GOOGLE, HASH, "ignored", "ignored@example.invalid"))
            .isEqualTo(new SocialAccountResult(42L, false));
        verifyNoInteractions(users);
    }

    @Test
    void nicknameIsTruncatedByCodePointAndOnlyHashIsSaved() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(users.createUser(any())).thenReturn(user);
        when(repository.findByAuthProviderAndProviderUserIdHash(AuthProvider.GOOGLE, HASH)).thenReturn(Optional.empty());
        String nickname = "😀".repeat(31);
        service.findOrCreate(AuthProvider.GOOGLE, HASH, nickname, "test@example.invalid");
        verify(users).createUser("😀".repeat(30));
        ArgumentCaptor<SocialAccount> account = ArgumentCaptor.forClass(SocialAccount.class);
        verify(repository).saveAndFlush(account.capture());
        assertThat(account.getValue().getProviderUserIdHash()).isEqualTo(HASH);
        assertThat(account.getValue().getEmail()).isEqualTo("test@example.invalid");
    }

    @Test
    void missingNicknameGetsDefaultAndOversizedEmailIsOmitted() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(users.createUser(any())).thenReturn(user);
        when(repository.findByAuthProviderAndProviderUserIdHash(AuthProvider.KAKAO, HASH)).thenReturn(Optional.empty());
        service.findOrCreate(AuthProvider.KAKAO, HASH, " ", "a".repeat(256));
        ArgumentCaptor<String> nickname = ArgumentCaptor.forClass(String.class);
        verify(users).createUser(nickname.capture());
        assertThat(nickname.getValue()).matches("우가차차_[0-9a-f]{8}");
        ArgumentCaptor<SocialAccount> account = ArgumentCaptor.forClass(SocialAccount.class);
        verify(repository).saveAndFlush(account.capture());
        assertThat(account.getValue().getEmail()).isNull();
    }
}
