package com.ject.oogachaka.auth.repository;

import static com.ject.oogachaka.global.crypto.TestCryptoKeys.dummyKey;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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

/** Docker의 실제 MySQL과 운영 서비스로 가입 트랜잭션을 검증한다. 소셜 제공자·Redis·토큰 발급은 목 객체로 대체한다. */
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

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        registry.add("app.crypto.active-version", () -> "v1");
        registry.add("app.crypto.aes-keys.v1", () -> dummyKey(1));
        registry.add("app.crypto.hmac-social-id-key", () -> dummyKey(2));
        registry.add("app.crypto.hmac-device-token-key", () -> dummyKey(3));
    }

    @BeforeEach
    void clearAccounts() {
        socialAccounts.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    // 기존 소셜 계정과 중복되는 계정을 새 사용자와 같은 트랜잭션에서 저장해 UNIQUE 위반을 발생시킨다.
    // 새 사용자 생성까지 롤백되고 기존 사용자의 계정만 남아 다시 조회되는지 확인한다.
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

    // 동일한 소셜 ID의 최초 로그인을 두 스레드에서 시작해도 두 응답이 같은 사용자 ID를 가리켜야 한다.
    // 신규 가입 응답은 하나뿐이며, DB에 사용자와 소셜 계정이 각각 한 건만 남는지 확인한다.
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
