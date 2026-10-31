package com.ject.oogachaka.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PushAsyncConfigTest {

    @Test
    void saturatedExecutorDoesNotRunPushWorkOnThePublisherThreadOrThrow() throws Exception {
        var properties = new PushAsyncProperties(1, 1, 0, "test-push-", Duration.ofSeconds(2));
        var executor = new PushAsyncConfig().pushExecutor(properties);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.initialize();
        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

            assertThatCode(() -> executor.execute(() -> {
                throw new AssertionError("포화 상태의 푸시가 요청 스레드에서 실행되었습니다.");
            })).doesNotThrowAnyException();
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void invalidPoolBoundsFailAtStartup() {
        assertThatThrownBy(() -> new PushAsyncProperties(4, 2, 100, "push-", Duration.ofSeconds(30)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PushAsyncProperties(1, 2, -1, "push-", Duration.ofSeconds(30)))
            .isInstanceOf(IllegalStateException.class);
    }
}
