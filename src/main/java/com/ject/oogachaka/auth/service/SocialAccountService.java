package com.ject.oogachaka.auth.service;

import com.ject.oogachaka.auth.entity.SocialAccount;
import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.auth.repository.SocialAccountRepository;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import com.ject.oogachaka.user.service.UserService;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 외부 API/Redis와 분리된 DB 트랜잭션. UNIQUE 경합은 호출자가 롤백 완료 후 재조회한다. */
@Service
@RequiredArgsConstructor
public class SocialAccountService {

    private final SocialAccountRepository socialAccountRepository;
    private final UserService userService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SocialAccountResult findOrCreate(AuthProvider provider, String hash, String nickname, String email) {
        Optional<SocialAccount> existing = socialAccountRepository.findByAuthProviderAndProviderUserIdHash(provider, hash);
        if (existing.isPresent()) {
            return new SocialAccountResult(existing.get().getUserId(), false);
        }
        Long userId = userService.createUser(createNickname(nickname)).getId();
        // 잘못된 제공자 이메일이 가입 전체를 실패시키지 않도록 컬럼 한도를 넘으면 저장하지 않는다.
        String storedEmail = email != null && email.codePointCount(0, email.length()) <= 255 ? email : null;
        socialAccountRepository.saveAndFlush(SocialAccount.create(userId, provider, hash, storedEmail));
        return new SocialAccountResult(userId, true);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public SocialAccountResult getExisting(AuthProvider provider, String hash) {
        SocialAccount account = socialAccountRepository.findByAuthProviderAndProviderUserIdHash(provider, hash)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT));
        return new SocialAccountResult(account.getUserId(), false);
    }

    private String createNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "우가차차_" + UUID.randomUUID().toString().substring(0, 8);
        }
        int end = nickname.offsetByCodePoints(0, Math.min(30, nickname.codePointCount(0, nickname.length())));
        return nickname.substring(0, end);
    }
}
