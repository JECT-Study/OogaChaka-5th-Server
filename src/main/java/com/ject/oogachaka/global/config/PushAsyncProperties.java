package com.ject.oogachaka.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.async.push")
public record PushAsyncProperties(int corePoolSize, int maxPoolSize, int queueCapacity,
                                  String threadNamePrefix, Duration awaitTermination) {

    public PushAsyncProperties {
        if (corePoolSize < 1 || maxPoolSize < corePoolSize || queueCapacity < 0
            || threadNamePrefix == null || threadNamePrefix.isBlank()
            || awaitTermination == null || awaitTermination.isNegative()) {
            throw new IllegalStateException("푸시 비동기 실행기 설정이 올바르지 않습니다.");
        }
    }
}
