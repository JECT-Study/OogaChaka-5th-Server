package com.ject.oogachaka.global.config;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.jasypt.salt.RandomSaltGenerator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

/**
 * 설정 복호화와 팀원용 암호화 도구에서 동일한 Jasypt 규격을 사용한다.
 */
final class JasyptEncryptorFactory {

    static final String PASSWORD_PROPERTY = "jasypt.encryptor.password";
    static final String PASSWORD_ENV = "JASYPT_ENCRYPTOR_PASSWORD";
    private static final String COMMITTED_CONFIG = "[application.properties]";

    private JasyptEncryptorFactory() {
    }

    static StandardPBEStringEncryptor create(String password) {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        encryptor.setPassword(password);
        encryptor.setKeyObtentionIterations(10000);
        encryptor.setSaltGenerator(new RandomSaltGenerator());
        encryptor.setIvGenerator(new RandomIvGenerator());
        encryptor.setStringOutputType("base64");
        encryptor.initialize();
        return encryptor;
    }

    /**
     * 서버·CI는 환경변수 JASYPT_ENCRYPTOR_PASSWORD, 로컬은 application-local.properties의
     * jasypt.encryptor.password를 읽는다. 환경변수는 relaxed binding으로 같은 프로퍼티가 되고 우선한다.
     */
    static String requirePassword(Environment environment) {
        String password = environment.getProperty(PASSWORD_PROPERTY);
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Jasypt 비밀번호가 없습니다. 로컬은 application-local.properties에 "
                + PASSWORD_PROPERTY + "를, 서버·CI는 환경변수 " + PASSWORD_ENV + "를 설정하세요.");
        }
        if (environment instanceof ConfigurableEnvironment configurable && definedInCommittedConfig(configurable)) {
            throw new IllegalStateException("Jasypt 비밀번호를 Git에 올라가는 application.properties에 적으면 안 됩니다. "
                + "application-local.properties 또는 환경변수 " + PASSWORD_ENV + "로 옮기세요.");
        }
        return password;
    }

    private static boolean definedInCommittedConfig(ConfigurableEnvironment environment) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source.getName().contains(COMMITTED_CONFIG) && source.containsProperty(PASSWORD_PROPERTY)) {
                return true;
            }
        }
        return false;
    }
}
