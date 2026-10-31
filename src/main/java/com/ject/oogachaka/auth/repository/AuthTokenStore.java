package com.ject.oogachaka.auth.repository;

import com.ject.oogachaka.auth.token.JwtClaims;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** Redis 장애 시 인증을 허용하지 않는다. 원문 토큰 대신 jti와 사용자 ID만 저장한다. */
@Repository
@RequiredArgsConstructor
public class AuthTokenStore {

    private static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
        if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
        redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[2])
        redis.call('DEL', KEYS[1])
        return 1
        """, Long.class);

    private static final DefaultRedisScript<Long> LOGOUT = new DefaultRedisScript<>("""
        local owner = redis.call('GET', KEYS[1])
        if owner and owner ~= ARGV[1] then return 0 end
        local ttl = tonumber(ARGV[2])
        if ttl > 0 then redis.call('SET', KEYS[2], '1', 'PX', ttl) end
        redis.call('DEL', KEYS[1])
        return 1
        """, Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    public void saveRefresh(JwtClaims refresh) {
        Boolean stored = execute(() -> redis.opsForValue().setIfAbsent(
            AuthRedisKeys.refresh(refresh.userId(), refresh.jti()), refresh.userId().toString(), remaining(refresh)));
        if (!Boolean.TRUE.equals(stored)) {
            throw unavailable();
        }
    }

    public boolean rotate(JwtClaims previous, JwtClaims next) {
        Long result = execute(() -> redis.execute(ROTATE, List.of(
            AuthRedisKeys.refresh(previous.userId(), previous.jti()),
            AuthRedisKeys.refresh(next.userId(), next.jti())),
            previous.userId().toString(), Long.toString(remaining(next).toMillis())));
        return Long.valueOf(1).equals(result);
    }

    public void logout(JwtClaims refresh, JwtClaims access) {
        long ttl = Math.max(0, Duration.between(clock.instant(), access.expiresAt()).toMillis());
        Long result = execute(() -> redis.execute(LOGOUT, List.of(
            AuthRedisKeys.refresh(refresh.userId(), refresh.jti()),
            AuthRedisKeys.blacklist(access.userId(), access.jti())),
            refresh.userId().toString(), Long.toString(ttl)));
        if (!Long.valueOf(1).equals(result)) {
            throw unavailable();
        }
    }

    public boolean isBlacklisted(JwtClaims access) {
        return Boolean.TRUE.equals(execute(() -> redis.hasKey(AuthRedisKeys.blacklist(access.userId(), access.jti()))));
    }

    private Duration remaining(JwtClaims claims) {
        Duration remaining = Duration.between(clock.instant(), claims.expiresAt());
        if (remaining.toMillis() < 1) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }
        return remaining;
    }

    private <T> T execute(Supplier<T> operation) {
        try {
            T result = operation.get();
            if (result == null) {
                throw unavailable();
            }
            return result;
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.AUTH_STORE_UNAVAILABLE);
    }
}
