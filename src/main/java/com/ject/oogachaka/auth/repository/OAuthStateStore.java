package com.ject.oogachaka.auth.repository;

import com.ject.oogachaka.global.config.OAuthProperties;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * OAuth 로그인 시도의 state를 Redis에 저장하고 한 번만 꺼내 쓰게 한다.
 * 저장값은 PKCE verifier다. PKCE를 쓰지 않는 네이버는 빈 문자열이다.
 */
@Repository
@RequiredArgsConstructor
public class OAuthStateStore {

    private final StringRedisTemplate redis;
    private final OAuthProperties properties;

    public void save(String state, String verifier) {
        redis.opsForValue().set(AuthRedisKeys.state(state), verifier, properties.getStateTtl());
    }

    // 프런트가 콜백 state를 자기 로그인 시도와 맞춰 보는 검증은 별도로 필요하다.
    public String consume(String state) {
        // GETDEL(Redis 6.2+)로 읽기와 삭제를 한 번에 해 같은 state를 두 요청이 쓰지 못하게 한다.
        String verifier = redis.opsForValue().getAndDelete(AuthRedisKeys.state(state));
        if (verifier == null) {
            throw new BusinessException(ErrorCode.AUTH_SOCIAL_LOGIN_FAILED);
        }
        return verifier;
    }
}
