package com.ject.oogachaka.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@EnableAsync
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PushAsyncProperties.class)
public class PushAsyncConfig {

    @Bean("pushExecutor")
    @DependsOn({"pushSender", "entityManagerFactory"})
    public ThreadPoolTaskExecutor pushExecutor(PushAsyncProperties properties) {
        // 의존성을 명시하여 종료 시 Firebase/JPA보다 먼저 푸시 작업을 정리한다.
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix(properties.threadNamePrefix());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationMillis(properties.awaitTermination().toMillis());
        // 커밋 콜백으로 거절 예외를 전파하거나 요청 스레드에서 외부 전송을 수행하지 않는다.
        executor.setRejectedExecutionHandler((task, pool) ->
            log.warn("푸시 실행기 포화 또는 종료로 작업을 생략했습니다. shutdown={}", pool.isShutdown()));
        return executor;
    }
}
