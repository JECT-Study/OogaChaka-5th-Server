package com.ject.oogachaka.auth.repository;

import com.ject.oogachaka.auth.token.JwtClaims;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** 갱신에 쓸 수 있는 refresh를 Redis에 보관한다. 원문 토큰 대신 사용자 ID와 jti만 저장한다. */
@Repository
@RequiredArgsConstructor
public class AuthTokenStore {

    private final StringRedisTemplate redis;

    // 키는 refresh JWT와 같은 시점에 만료된다.
    public void saveRefresh(JwtClaims refresh) {
        redis.opsForValue().set(AuthRedisKeys.refresh(refresh.userId(), refresh.jti()), refresh.userId().toString(),
            Duration.between(refresh.issuedAt(), refresh.expiresAt()));
    }

    // 이전 refresh 키를 지운 요청만 새 refresh를 등록한다. 같은 refresh로 동시에 갱신하면 DEL에 성공한 한 요청만 통과한다.
    public boolean rotate(JwtClaims previous, JwtClaims next) {
        if (!Boolean.TRUE.equals(redis.delete(AuthRedisKeys.refresh(previous.userId(), previous.jti())))) {
            return false;
        }

        saveRefresh(next);
        return true;
    }

    public void deleteRefresh(JwtClaims refresh) {
        redis.delete(AuthRedisKeys.refresh(refresh.userId(), refresh.jti()));
    }
}
