package com.ject.oogachaka.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.mission.event.MissionCompletionRequestedEvent;
import java.util.Optional;
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
class MissionCompletionPushTransactionTest {

    private final MissionCompletionRequestedEvent event = new MissionCompletionRequestedEvent(10L, 3L, "책 읽기");

    @Test
    void committedEventWithLongTitleRunsAfterCommitOnTheDedicatedExecutor() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            var port = context.getBean(ParentPushRecipientPort.class);
            var manager = context.getBean(TestTransactionManager.class);
            Thread publisherThread = Thread.currentThread();
            AtomicReference<Thread> listenerThread = new AtomicReference<>();
            AtomicReference<Boolean> committedWhenCalled = new AtomicReference<>();
            CountDownLatch called = new CountDownLatch(1);
            when(port.findByChildUserId(3L)).thenAnswer(invocation -> {
                listenerThread.set(Thread.currentThread());
                committedWhenCalled.set(manager.committed);
                called.countDown();
                return Optional.empty();
            });

            new TransactionTemplate(manager).executeWithoutResult(status -> {
                context.publishEvent(new MissionCompletionRequestedEvent(10L, 3L, "가".repeat(100)));
                verifyNoInteractions(port);
            });

            assertThat(called.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(committedWhenCalled.get()).isTrue();
            assertThat(listenerThread.get()).isNotSameAs(publisherThread);
            assertThat(listenerThread.get().getName()).startsWith("test-push-");
        }
    }

    @Test
    void rolledBackAndNonTransactionalEventsDoNotSend() {
        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            var port = context.getBean(ParentPushRecipientPort.class);

            new TransactionTemplate(context.getBean(TestTransactionManager.class)).executeWithoutResult(status -> {
                context.publishEvent(event);
                status.setRollbackOnly();
            });
            context.publishEvent(event);

            verifyNoInteractions(port, context.getBean(PushSender.class));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAsync
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        ParentPushRecipientPort recipientPort() {
            return mock(ParentPushRecipientPort.class);
        }

        @Bean
        PushSender pushSender() {
            return mock(PushSender.class);
        }

        @Bean
        DeviceTokenService deviceTokenService() {
            return mock(DeviceTokenService.class);
        }

        @Bean
        MissionCompletionPushListener listener(ParentPushRecipientPort port, PushSender sender, DeviceTokenService service) {
            return new MissionCompletionPushListener(port, sender, service);
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

        private volatile boolean committed;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            committed = false;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
