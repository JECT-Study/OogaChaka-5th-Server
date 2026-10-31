package com.ject.oogachaka.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.auth.dto.OAuthState;
import com.ject.oogachaka.auth.dto.SocialLoginRequest;
import com.ject.oogachaka.auth.dto.TokenResponse;
import com.ject.oogachaka.auth.entity.SocialAccount;
import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.auth.service.SocialAccountService;
import com.ject.oogachaka.auth.service.SocialLoginService;
import com.ject.oogachaka.auth.service.TokenService;
import com.ject.oogachaka.global.config.CryptoConfig;
import com.ject.oogachaka.global.config.JpaConfig;
import com.ject.oogachaka.global.config.OAuthProperties;
import com.ject.oogachaka.global.crypto.HmacPurpose;
import com.ject.oogachaka.global.crypto.HmacService;
import com.ject.oogachaka.global.infrastructure.client.social.SocialLoginClient;
import com.ject.oogachaka.global.infrastructure.client.social.SocialUserInfo;
import com.ject.oogachaka.user.entity.User;
import com.ject.oogachaka.user.repository.UserRepository;
import com.ject.oogachaka.user.service.UserService;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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

/** 운영 엔티티/Repository와 V1+V2를 격리된 MySQL에서 검증한다. 이번 작업에서는 실행하지 않는다. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate", showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({JpaConfig.class, CryptoConfig.class, HmacService.class, UserService.class, SocialAccountService.class})
@ActiveProfiles("test")
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SocialAccountRepositoryIntegrationTest {

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    private SocialAccountRepository socialAccounts;
    @Autowired
    private UserRepository users;
    @Autowired
    private SocialAccountService accounts;
    @Autowired
    private UserService userService;
    @Autowired
    private HmacService hmac;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void clearAccounts() {
        socialAccounts.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void v2CharMappingAndHmacLookupUseProductionEntities() {
        String hash = hash();
        User user = users.saveAndFlush(User.create("별명"));
        socialAccounts.saveAndFlush(SocialAccount.create(user.getId(), AuthProvider.GOOGLE, hash, null));

        SocialAccount account = socialAccounts.findByAuthProviderAndProviderUserIdHash(AuthProvider.GOOGLE,
            hmac.hash("TEST_ONLY_RAW_ID", HmacPurpose.SOCIAL_ID)).orElseThrow();
        assertThat(account.getUserId()).isEqualTo(user.getId());
        assertThat(account.getProviderUserIdHash()).isEqualTo(hash);
        assertThat(account.getCreatedAt()).isNotNull();
        assertThat(account.getUpdatedAt()).isNotNull();
        User persisted = users.findById(user.getId()).orElseThrow();
        assertThat(persisted.getPoint()).isZero();
        assertThat(persisted.getFamilyMemberId()).isNull();
        assertThat(persisted.getCreatedAt()).isNotNull();
        var metadata = new JdbcTemplate(dataSource).queryForMap(
            "SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, COLLATION_NAME FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'social_account' AND COLUMN_NAME = 'provider_user_id_hash'");
        assertThat(metadata.get("DATA_TYPE")).isEqualTo("char");
        assertThat(((Number) metadata.get("CHARACTER_MAXIMUM_LENGTH")).intValue()).isEqualTo(64);
        assertThat(metadata.get("COLLATION_NAME")).isEqualTo("ascii_bin");
    }

    @Test
    void compositeUniqueRejectsDuplicateButSeparatesProvidersAndKeepsUserUnique() {
        User first = users.saveAndFlush(User.create("첫번째"));
        User second = users.saveAndFlush(User.create("두번째"));
        socialAccounts.saveAndFlush(SocialAccount.create(first.getId(), AuthProvider.GOOGLE, hash(), null));

        assertThatThrownBy(() -> socialAccounts.saveAndFlush(
            SocialAccount.create(second.getId(), AuthProvider.GOOGLE, hash(), null)))
            .isInstanceOf(DataIntegrityViolationException.class);
        socialAccounts.saveAndFlush(SocialAccount.create(second.getId(), AuthProvider.KAKAO, hash(), null));
        assertThat(socialAccounts.findByAuthProviderAndProviderUserIdHash(AuthProvider.KAKAO, hash()).orElseThrow().getUserId())
            .isEqualTo(second.getId());
        assertThatThrownBy(() -> socialAccounts.saveAndFlush(
            SocialAccount.create(first.getId(), AuthProvider.NAVER, "b".repeat(64), null)))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(socialAccounts.count()).isEqualTo(2);
    }

    @Test
    void actualServiceRegistersOnceAndLoadsExistingAccountInNewTransaction() {
        var first = accounts.findOrCreate(AuthProvider.GOOGLE, hash(), "별명", "test@example.invalid");
        var second = accounts.findOrCreate(AuthProvider.GOOGLE, hash(), "새별명", null);

        assertThat(first.isNewUser()).isTrue();
        assertThat(second.isNewUser()).isFalse();
        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(users.count()).isEqualTo(1);
        assertThat(socialAccounts.count()).isEqualTo(1);
        assertThat(accounts.getExisting(AuthProvider.GOOGLE, hash()).userId()).isEqualTo(first.userId());
    }

    @Test
    void uniqueFailureRollsBackNewUserBeforeWinnerIsReloaded() {
        var winner = accounts.findOrCreate(AuthProvider.GOOGLE, hash(), "승자", null);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            User losingUser = userService.createUser("경합패자");
            socialAccounts.saveAndFlush(SocialAccount.create(losingUser.getId(), AuthProvider.GOOGLE, hash(), null));
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(accounts.getExisting(AuthProvider.GOOGLE, hash()).userId()).isEqualTo(winner.userId());
        assertThat(users.count()).isEqualTo(1);
        assertThat(socialAccounts.count()).isEqualTo(1);
    }

    @Test
    void concurrentFirstLoginsReturnOneNewUserAndLeaveNoOrphanUser() throws Exception {
        OAuthProperties properties = mock(OAuthProperties.class);
        OAuthStateStore states = mock(OAuthStateStore.class);
        TokenService tokens = mock(TokenService.class);
        SocialLoginClient client = mock(SocialLoginClient.class);
        when(properties.getEnabledProvider(AuthProvider.GOOGLE)).thenReturn(mock(OAuthProperties.Provider.class));
        when(states.consume(anyString(), eq(AuthProvider.GOOGLE))).thenReturn(new OAuthState(AuthProvider.GOOGLE, "v".repeat(43)));
        when(client.getProvider()).thenReturn(AuthProvider.GOOGLE);
        CyclicBarrier ready = new CyclicBarrier(2);
        when(client.getUserInfo(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            ready.await(10, TimeUnit.SECONDS);
            return new SocialUserInfo("TEST_ONLY_RAW_ID", "별명", null);
        });
        when(tokens.issue(org.mockito.ArgumentMatchers.anyLong()))
            .thenReturn(new TokenResponse("Bearer", "TEST_ONLY_ACCESS", "TEST_ONLY_REFRESH", 1800, 1209600));
        SocialLoginService login = new SocialLoginService(properties, states, hmac, accounts, tokens, List.of(client));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> login.login(new SocialLoginRequest(AuthProvider.GOOGLE, "TEST_ONLY_CODE_A", "a".repeat(43))));
            var b = executor.submit(() -> login.login(new SocialLoginRequest(AuthProvider.GOOGLE, "TEST_ONLY_CODE_B", "b".repeat(43))));
            var first = a.get(20, TimeUnit.SECONDS);
            var second = b.get(20, TimeUnit.SECONDS);
            assertThat(first.userId()).isEqualTo(second.userId());
            assertThat(List.of(first.isNewUser(), second.isNewUser())).containsExactlyInAnyOrder(true, false);
            assertThat(users.count()).isEqualTo(1);
            assertThat(socialAccounts.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private String hash() {
        return hmac.hash("TEST_ONLY_RAW_ID", HmacPurpose.SOCIAL_ID);
    }
}
