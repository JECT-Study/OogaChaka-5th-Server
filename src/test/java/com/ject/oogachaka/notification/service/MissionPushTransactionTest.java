package com.ject.oogachaka.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.mission.event.MissionCompletionRequestedEvent;
import com.ject.oogachaka.family.service.FamilyService;
import com.ject.oogachaka.user.entity.User;
import com.ject.oogachaka.user.service.UserService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** DB 없이 Spring의 실제 이벤트/트랜잭션/Async 프록시가 연결되는 경계를 검증한다. */
class MissionPushTransactionTest {

    private final MissionCompletionRequestedEvent event = new MissionCompletionRequestedEvent(10L, 3L, "책 읽기");

    // 트랜잭션 안에서 발행한 이벤트는 커밋된 뒤 푸시 전용 스레드에서 처리된다.
    @Test
    void committedEventRunsAfterCommitOnTheDedicatedExecutor() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            var userService = context.getBean(UserService.class);
            var manager = context.getBean(TestTransactionManager.class);
            Thread publisherThread = Thread.currentThread();
            AtomicReference<Thread> listenerThread = new AtomicReference<>();
            AtomicReference<Boolean> committedWhenCalled = new AtomicReference<>();
            CountDownLatch called = new CountDownLatch(1);
            when(userService.getUser(3L)).thenAnswer(invocation -> {
                listenerThread.set(Thread.currentThread());
                committedWhenCalled.set(manager.commitCount > 0);
                called.countDown();
                return User.create("하늘");
            });

            new TransactionTemplate(manager).executeWithoutResult(status -> {
                context.publishEvent(event);
                verifyNoInteractions(userService);
            });

            assertThat(called.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(committedWhenCalled.get()).isTrue();
            assertThat(listenerThread.get()).isNotSameAs(publisherThread);
            assertThat(listenerThread.get().getName()).startsWith("test-push-");
        }
    }

    // 롤백되었거나 트랜잭션 밖에서 발행한 이벤트는 푸시를 보내지 않는다.
    @Test
    void rolledBackAndNonTransactionalEventsDoNotSend() {
        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            var userService = context.getBean(UserService.class);

            new TransactionTemplate(context.getBean(TestTransactionManager.class)).executeWithoutResult(status -> {
                context.publishEvent(event);
                status.setRollbackOnly();
            });
            context.publishEvent(event);

            verifyNoInteractions(userService, context.getBean(PushService.class));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAsync
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        UserService userService() {
            return mock(UserService.class);
        }

        @Bean
        FamilyService familyService() {
            return mock(FamilyService.class);
        }

        @Bean
        PushService pushService() {
            return mock(PushService.class);
        }

        @Bean
        MissionPushListener listener(UserService userService, FamilyService familyService, PushService service) {
            return new MissionPushListener(userService, familyService, service);
        }

        @Bean
        TestTransactionManager transactionManager() {
            return new TestTransactionManager();
        }

        @Bean("pushExecutor")
        ThreadPoolTaskExecutor pushExecutor() {
            var executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setThreadNamePrefix("test-push-");
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(2);
            return executor;
        }
    }

    static class TestTransactionManager extends AbstractPlatformTransactionManager {

        // 리스너가 부르는 서비스도 트랜잭션을 열 수 있어, 시작 시 초기화하지 않는 커밋 횟수로 기록한다.
        private volatile int commitCount;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
