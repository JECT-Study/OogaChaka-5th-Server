package com.ject.oogachaka.family.repository;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 가족별로 현재 유효한 자녀 초대코드를 Redis에 보관한다.
 * 자녀 첫 입장 API(5-39)는 consume으로 코드를 한 번만 꺼내 쓴다.
 */
@Repository
@RequiredArgsConstructor
public class InviteCodeStore {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int CODE_LENGTH = 6;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_ATTEMPTS = 5;

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public record InviteCode(String code, OffsetDateTime expiresAt) {
    }

    public InviteCode issue(Long familyId) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = generateCode();
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(codeKey(code), familyId.toString(), TTL))) {
                continue;
            }
            // 가족 키를 원자적으로 교체해야 동시 재발급에서도 각 요청이 직전 코드 키를 지울 수 있다.
            String previousCode = redis.opsForValue().setGet(familyKey(familyId), code, TTL);
            if (previousCode != null) {
                redis.delete(codeKey(previousCode));
            }
            return new InviteCode(code, now().plus(TTL));
        }
        throw new IllegalStateException("사용 가능한 가족 초대코드를 생성하지 못했습니다.");
    }

    public Optional<InviteCode> find(Long familyId) {
        String key = familyKey(familyId);
        String code = redis.opsForValue().get(key);
        if (code == null) {
            return Optional.empty();
        }
        long remainingSeconds = redis.getExpire(key);
        if (remainingSeconds < 0) {
            return Optional.empty();
        }
        return Optional.of(new InviteCode(code, now().plusSeconds(remainingSeconds)));
    }

    public Optional<Long> consume(String code) {
        String value = redis.opsForValue().getAndDelete(codeKey(code));
        if (value == null) {
            return Optional.empty();
        }
        Long familyId = Long.valueOf(value);
        redis.delete(familyKey(familyId));
        return Optional.of(familyId);
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private String codeKey(String code) {
        return "oogachaka:family:invite:code:" + code;
    }

    private String familyKey(Long familyId) {
        return "oogachaka:family:invite:family:" + familyId;
    }
}
