package com.ject.oogachaka.auth.repository;

import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.auth.service.OAuthState;
import com.ject.oogachaka.global.config.OAuthProperties;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OAuthStateStore {

    private final StringRedisTemplate redis;
    private final OAuthProperties properties;

    public boolean save(String state, OAuthState value) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(AuthRedisKeys.state(state),
                value.provider().name() + ":" + value.verifier(), properties.getStateTtl()));
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.AUTH_STORE_UNAVAILABLE);
        }
    }

    public OAuthState consume(String state, AuthProvider expectedProvider) {
        if (state == null || !state.matches("[A-Za-z0-9_-]{43}")) {
            throw invalidState();
        }
        String stored;
        try {
            // Redis 6.2+ GETDEL: state 검증과 폐기를 한 번에 수행한다.
            stored = redis.opsForValue().getAndDelete(AuthRedisKeys.state(state));
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.AUTH_STORE_UNAVAILABLE);
        }
        String prefix = expectedProvider.name() + ":";
        if (stored == null || !stored.startsWith(prefix)) {
            throw invalidState();
        }
        return new OAuthState(expectedProvider, stored.substring(prefix.length()));
    }

    private BusinessException invalidState() {
        return new BusinessException(ErrorCode.AUTH_STATE_INVALID);
    }
}
