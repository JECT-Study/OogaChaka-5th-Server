package com.ject.oogachaka.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ject.oogachaka.global.config.CryptoConfig;
import com.ject.oogachaka.global.config.JpaConfig;
import com.ject.oogachaka.global.crypto.EncryptionConverter;
import com.ject.oogachaka.global.crypto.EncryptionService;
import com.ject.oogachaka.global.crypto.HmacPurpose;
import com.ject.oogachaka.global.crypto.HmacService;
import com.ject.oogachaka.notification.dto.PushRegistrationRequest;
import com.ject.oogachaka.notification.entity.DeviceToken;
import com.ject.oogachaka.notification.enums.DevicePlatform;
import com.ject.oogachaka.notification.service.DeviceTokenService;
import com.ject.oogachaka.notification.service.PushRegistrationService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Mockito로 대체할 수 없는 가족 역할 필터와 실제 Converter/스키마 매핑을 검증한다. 실행은 추후 수행한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({CryptoConfig.class, JpaConfig.class, EncryptionConverter.class, EncryptionService.class, HmacService.class,
    FamilyParentPushRecipientQuery.class, DeviceTokenService.class, PushRegistrationService.class})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class FamilyParentPushRecipientQueryTest {

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DeviceTokenRepository repository;

    @Autowired
    private FamilyParentPushRecipientQuery query;

    @Autowired
    private DeviceTokenService tokenService;

    @Autowired
    private PushRegistrationService registrationService;

    @Autowired
    private HmacService hmacService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void prepareFamilies() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM device_token");
            jdbc.update("DELETE FROM users");
            jdbc.update("DELETE FROM family_member");
            insertMember(1L, 100L, "PARENT", "부모1");
            insertMember(2L, 100L, "PARENT", "부모2");
            insertMember(3L, 100L, "CHILD", "자녀");
            insertMember(4L, 100L, "CHILD", "형제");
            insertMember(5L, 200L, "PARENT", "다른 가족 부모");
            saveDevice(1L, "dummy-parent1-android", DevicePlatform.ANDROID);
            saveDevice(1L, "dummy-parent1-ios", DevicePlatform.IOS);
            saveDevice(2L, "dummy-parent2-web", DevicePlatform.WEB);
            saveDevice(3L, "dummy-child", DevicePlatform.ANDROID);
            saveDevice(4L, "dummy-sibling", DevicePlatform.IOS);
            saveDevice(5L, "dummy-other-family", DevicePlatform.WEB);
        });
    }

    @Test
    void loadsOnlySameFamilyParentsAndDecryptsTokensThroughJpa() {
        var recipients = query.findByChildUserId(3L).orElseThrow();

        assertThat(recipients.childNickname()).isEqualTo("자녀");
        assertThat(recipients.devices()).hasSize(3);
        assertThat(recipients.devices()).extracting(DeviceToken::getUserId).containsOnly(1L, 2L);
        assertThat(recipients.devices()).extracting(DeviceToken::getToken)
            .containsExactlyInAnyOrder("dummy-parent1-android", "dummy-parent1-ios", "dummy-parent2-web");
        // 조회 검증에는 token 원문을 JDBC로 읽지 않고, 저장 형태의 접두어/불일치 여부만 DB에서 비교한다.
        Integer encryptedCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM device_token WHERE token LIKE 'v1:%' AND token <> ?", Integer.class,
            "dummy-parent1-android");
        assertThat(encryptedCount).isEqualTo(6);
    }

    @Test
    void parentOrUnknownPublisherDoesNotResolveAsAChild() {
        assertThat(query.findByChildUserId(1L)).isEmpty();
        assertThat(query.findByChildUserId(999L)).isEmpty();
    }

    @Test
    void upsertMovesOwnershipAndPreservesGlobalUniqueness() {
        String token = "dummy-child";
        String hash = hmacService.hash(token, HmacPurpose.DEVICE_TOKEN);

        tokenService.upsert(2L, token, hash, DevicePlatform.IOS);
        tokenService.upsert(2L, token, hash, DevicePlatform.IOS);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device_token WHERE token_hash = ?", Integer.class, hash))
            .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT user_id FROM device_token WHERE token_hash = ?", Long.class, hash))
            .isEqualTo(2L);
        assertThat(query.findByChildUserId(3L).orElseThrow().devices()).hasSize(4);
    }

    @Test
    void staleInvalidSnapshotCannotDeleteARegistrationThatHasBeenSyncedAgain() {
        DeviceToken oldSnapshot = query.findByChildUserId(3L).orElseThrow().devices().get(0);
        tokenService.upsert(oldSnapshot.getUserId(), oldSnapshot.getToken(), oldSnapshot.getTokenHash(), DevicePlatform.WEB);

        tokenService.deleteInvalidRegistrations(java.util.List.of(oldSnapshot));
        assertThat(repository.existsById(oldSnapshot.getId())).isTrue();

        DeviceToken latest = repository.findById(oldSnapshot.getId()).orElseThrow();
        tokenService.deleteInvalidRegistrations(java.util.List.of(latest));
        assertThat(repository.existsById(oldSnapshot.getId())).isFalse();
    }

    @Test
    void concurrentFirstRegistrationConvergesToASingleTokenRow() throws Exception {
        String token = "dummy-concurrent-new-token";
        String hash = hmacService.hash(token, HmacPurpose.DEVICE_TOKEN);
        CountDownLatch start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> {
                start.await();
                registrationService.register(1L, new PushRegistrationRequest(token, DevicePlatform.IOS));
                return null;
            });
            var second = workers.submit(() -> {
                start.await();
                registrationService.register(2L, new PushRegistrationRequest(token, DevicePlatform.ANDROID));
                return null;
            });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device_token WHERE token_hash = ?", Integer.class, hash))
                .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT user_id FROM device_token WHERE token_hash = ?", Long.class, hash))
                .isIn(1L, 2L);
        } finally {
            workers.shutdownNow();
        }
    }

    private void insertMember(Long userId, Long familyId, String role, String nickname) {
        jdbc.update("INSERT INTO family_member (id, family_id, family_role, created_at, updated_at) "
            + "VALUES (?, ?, ?, NOW(6), NOW(6))", userId, familyId, role);
        jdbc.update("INSERT INTO users (id, family_member_id, nickname, created_at, updated_at) "
            + "VALUES (?, ?, ?, NOW(6), NOW(6))", userId, userId, nickname);
    }

    private void saveDevice(Long userId, String token, DevicePlatform platform) {
        repository.saveAndFlush(DeviceToken.create(userId, token, hmacService.hash(token, HmacPurpose.DEVICE_TOKEN), platform));
    }
}
