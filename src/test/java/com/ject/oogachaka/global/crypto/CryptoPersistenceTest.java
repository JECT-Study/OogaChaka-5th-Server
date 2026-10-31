package com.ject.oogachaka.global.crypto;

import static com.ject.oogachaka.global.crypto.TestCryptoKeys.dummyKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ject.oogachaka.global.config.CryptoConfig;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import testsupport.crypto.CryptoTestValue;

/** 실제 DB 대신 Docker의 일회용 MySQL에서만 마이그레이션과 Converter를 검증한다. */
@Testcontainers(disabledWithoutDocker = true)
class CryptoPersistenceTest {

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void createContext() {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("dummy-crypto-keys", Map.of(
            "app.crypto.active-version", "v1", "app.crypto.aes-keys.v1", dummyKey(1),
            "app.crypto.hmac-social-id-key", dummyKey(2), "app.crypto.hmac-device-token-key", dummyKey(3))));
        context.register(CryptoConfig.class, EncryptionService.class, EncryptionConverter.class,
            HmacService.class, PersistenceConfig.class);
        context.refresh();
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    }

    @AfterAll
    static void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void clearFixtures() {
        jdbc.update("DELETE FROM crypto_test_value");
        jdbc.update("DELETE FROM social_account");
        jdbc.update("DELETE FROM device_token");
    }

    @Test
    void jpaUsesInjectedConverterAndStoresCiphertextWithinTextCapacity() {
        String original = "가".repeat(16000);
        var entityManager = context.getBean(EntityManagerFactory.class).createEntityManager();
        try {
            entityManager.getTransaction().begin();
            entityManager.persist(new CryptoTestValue(1L, original));
            entityManager.persist(new CryptoTestValue(2L, null));
            entityManager.getTransaction().commit();
            entityManager.clear();

            String stored = jdbc.queryForObject("SELECT secret FROM crypto_test_value WHERE id = 1", String.class);
            int expectedLength = 3 + 4 * ((12 + original.getBytes(StandardCharsets.UTF_8).length + 16 + 2) / 3);
            assertThat(stored).startsWith("v1:").isNotEqualTo(original).hasSize(expectedLength);
            assertThat(stored.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(65535);
            assertThat(entityManager.find(CryptoTestValue.class, 1L).getSecret()).isEqualTo(original);
            assertThat(entityManager.find(CryptoTestValue.class, 2L).getSecret()).isNull();
            assertThat(jdbc.queryForObject("SELECT secret FROM crypto_test_value WHERE id = 2", String.class)).isNull();
        } finally {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
    }

    @Test
    void hmacLookupAndCompositeUniqueSeparateProviders() {
        HmacService service = context.getBean(HmacService.class);
        String hash = service.hash("same-provider-id", HmacPurpose.SOCIAL_ID);
        insertSocialAccount(1L, "GOOGLE", hash);

        assertThat(jdbc.queryForObject(
            "SELECT user_id FROM social_account WHERE auth_provider = ? AND provider_user_id_hash = ?",
            Long.class, "GOOGLE", service.hash("same-provider-id", HmacPurpose.SOCIAL_ID))).isEqualTo(1L);
        assertThatThrownBy(() -> insertSocialAccount(2L, "GOOGLE", hash)).isInstanceOf(DuplicateKeyException.class);
        insertSocialAccount(2L, "KAKAO", hash);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM social_account", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> insertSocialAccount(1L, "NAVER", "0".repeat(64)))
            .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void deviceTokenHashIsGloballyUniqueAndHashesUseAsciiBinaryColumns() {
        String hash = context.getBean(HmacService.class).hash("dummy-fcm-token", HmacPurpose.DEVICE_TOKEN);
        String encrypted = context.getBean(EncryptionService.class).encrypt("dummy-fcm-token");
        String sql = "INSERT INTO device_token (user_id, token, token_hash, device_platform, last_synced_at, "
            + "created_at, updated_at) VALUES (?, ?, ?, 'ANDROID', NOW(6), NOW(6), NOW(6))";
        jdbc.update(sql, 1L, encrypted, hash);
        assertThatThrownBy(() -> jdbc.update(sql, 2L, encrypted, hash)).isInstanceOf(DuplicateKeyException.class);

        for (String column : new String[] {"provider_user_id_hash", "token_hash"}) {
            Map<String, Object> metadata = jdbc.queryForMap("SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, "
                + "CHARACTER_SET_NAME, COLLATION_NAME, IS_NULLABLE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = ?", column);
            assertThat(metadata.get("DATA_TYPE")).isEqualTo("char");
            assertThat(((Number) metadata.get("CHARACTER_MAXIMUM_LENGTH")).intValue()).isEqualTo(64);
            assertThat(metadata.get("CHARACTER_SET_NAME")).isEqualTo("ascii");
            assertThat(metadata.get("COLLATION_NAME")).isEqualTo("ascii_bin");
            assertThat(metadata.get("IS_NULLABLE")).isEqualTo("NO");
        }
    }

    private void insertSocialAccount(Long userId, String provider, String hash) {
        jdbc.update("INSERT INTO social_account (user_id, auth_provider, provider_user_id_hash, created_at, updated_at) "
            + "VALUES (?, ?, ?, NOW(6), NOW(6))", userId, provider, hash);
    }

    @Configuration(proxyBeanMethods = false)
    static class PersistenceConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
            Flyway.configure().dataSource(source).load().migrate();
            new JdbcTemplate(source).execute("CREATE TABLE crypto_test_value (id BIGINT PRIMARY KEY, secret TEXT NULL)");
            return source;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source, HibernatePropertiesCustomizer customizer) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("testsupport.crypto");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            Map<String, Object> properties = new HashMap<>();
            properties.put("hibernate.hbm2ddl.auto", "validate");
            customizer.customize(properties);
            factory.setJpaPropertyMap(properties);
            return factory;
        }
    }
}
