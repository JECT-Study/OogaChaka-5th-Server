package com.ject.oogachaka.family.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.family.repository.InviteCodeStore.InviteCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** 초대코드 발급·조회·소비에 사용하는 키와 TTL을 검증한다. Redis 명령은 목 객체로 대체한다. */
@ExtendWith(MockitoExtension.class)
class InviteCodeStoreTest {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String FAMILY_KEY = "oogachaka:family:invite:family:5";
    private static final String CODE_PREFIX = "oogachaka:family:invite:code:";

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    private InviteCodeStore store;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        Clock clock = Clock.fixed(Instant.parse("2026-11-24T05:00:00.987Z"), ZoneOffset.UTC);
        store = new InviteCodeStore(redis, clock);
    }

    // 이전 코드가 있는 가족에 재발급하면 새 6자리 코드를 양쪽 키에 5분간 저장하고 이전 코드 키를 지운다.
    @Test
    void 가족의_코드를_교체하고_새_만료_시각을_반환한다() {
        when(values.setIfAbsent(anyString(), eq("5"), eq(TTL))).thenReturn(true);
        when(values.setGet(eq(FAMILY_KEY), anyString(), eq(TTL))).thenReturn("K7MP2Q");

        InviteCode issued = store.issue(5L);

        assertThat(issued.code()).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}");
        assertThat(issued.expiresAt()).isEqualTo(OffsetDateTime.parse("2026-11-24T05:05:00Z"));
        verify(values).setIfAbsent(CODE_PREFIX + issued.code(), "5", TTL);
        verify(values).setGet(FAMILY_KEY, issued.code(), TTL);
        verify(redis).delete(CODE_PREFIX + "K7MP2Q");
    }

    // 첫 후보가 사용 중이면 다른 코드 키를 확보한 뒤 성공한 코드를 가족 키에 저장하고 반환한다.
    @Test
    void 이미_쓰는_코드면_다른_코드로_발급한다() {
        Set<String> usedKeys = new HashSet<>();
        when(values.setIfAbsent(anyString(), eq("5"), eq(TTL))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (usedKeys.isEmpty()) {
                usedKeys.add(key);
            }
            return !usedKeys.contains(key);
        });

        InviteCode issued = store.issue(5L);

        assertThat(usedKeys).doesNotContain(CODE_PREFIX + issued.code());
        verify(values).setIfAbsent(CODE_PREFIX + issued.code(), "5", TTL);
        verify(values).setGet(FAMILY_KEY, issued.code(), TTL);
    }

    // 다섯 후보가 모두 사용 중이면 가족의 현재 코드를 바꾸지 않고 발급에 실패한다.
    @Test
    void 다섯_번_충돌하면_발급을_중단한다() {
        when(values.setIfAbsent(anyString(), eq("5"), eq(TTL))).thenReturn(false);

        assertThatIllegalStateException().isThrownBy(() -> store.issue(5L));

        verify(values, times(5)).setIfAbsent(anyString(), eq("5"), eq(TTL));
        verify(values, never()).setGet(eq(FAMILY_KEY), anyString(), eq(TTL));
    }

    // 가족 키가 가리키는 코드 키의 TTL이 72초면 현재 시각에서 72초 뒤를 만료 시각으로 반환한다.
    @Test
    void 코드_키의_남은_시간으로_만료_시각을_계산한다() {
        when(values.get(FAMILY_KEY)).thenReturn("K7MP2Q");
        when(redis.getExpire(CODE_PREFIX + "K7MP2Q")).thenReturn(72L);

        assertThat(store.find(5L)).contains(new InviteCode("K7MP2Q", OffsetDateTime.parse("2026-11-24T05:01:12Z")));
    }

    // 가족 키에 이미 꺼내 쓴 코드가 남아 있어도 코드 키가 없으면 조회 결과는 비어 있다.
    @Test
    void 가족_키에_남은_사용된_코드는_조회되지_않는다() {
        when(values.get(FAMILY_KEY)).thenReturn("K7MP2Q");
        when(redis.getExpire(CODE_PREFIX + "K7MP2Q")).thenReturn(-2L);

        assertThat(store.find(5L)).isEmpty();
    }

    // 코드 키에서 가족 ID 문자열을 꺼내면 숫자로 변환해 반환한다.
    @Test
    void 코드를_꺼내면_해당_가족_ID를_반환한다() {
        when(values.getAndDelete(CODE_PREFIX + "K7MP2Q")).thenReturn("5");

        assertThat(store.consume("K7MP2Q")).contains(5L);
    }
}
