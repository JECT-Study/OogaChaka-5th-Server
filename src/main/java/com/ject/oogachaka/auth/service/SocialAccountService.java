package com.ject.oogachaka.auth.service;

import com.ject.oogachaka.auth.entity.SocialAccount;
import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.auth.repository.SocialAccountRepository;
import com.ject.oogachaka.user.service.UserService;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 *  외부 API/Redis와 분리된 DB 트랜잭션.
 *  외부 통신과 분리된 DB 계정 조회·가입의 단위다. 사용자와 소셜 계정 저장을 같은 트랜잭션으로 묶는다.
 *  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SocialAccountService {

    // 이 도메인의 소셜 계정을 제공자+HMAC으로 조회하고 저장한다.
    private final SocialAccountRepository socialAccountRepository;
    private final UserService userService;

    // 기존 계정이면 그 사용자를 반환하고, 없으면 사용자+소셜 계정을 함께 생성
    @Transactional
    public SocialAccountResult findOrCreate(AuthProvider provider, String hash, String nickname, String email) {
        Optional<SocialAccount> socialAccountOptional = socialAccountRepository.findByAuthProviderAndProviderUserIdHash(provider, hash);
        if (socialAccountOptional.isPresent()) {
            return new SocialAccountResult(socialAccountOptional.get().getUserId(), false);
        }

        Long userId = userService.createUser(createNickname(nickname)).getId();
        socialAccountRepository.save(SocialAccount.create(userId, provider, hash, email));
        return new SocialAccountResult(userId, true);
    }

    private String createNickname(String nickname) {
        // 닉네임 동의를 하지 않았거나 공백뿐이어도 자동 가입을 계속할 수 있게 한다.
        if (nickname == null || nickname.isBlank()) {
            return "우가차차_" + UUID.randomUUID().toString().substring(0, 8);
        }

        //  SQL VARCHAR(30)에 맞추되 이모지의 UTF-16 쌍을 중간에서 자르지 않도록 코드 포인트 수로 끝 위치를 계산한다.
        int end = nickname.offsetByCodePoints(0, Math.min(30, nickname.codePointCount(0, nickname.length())));
        return nickname.substring(0, end);
    }
}
