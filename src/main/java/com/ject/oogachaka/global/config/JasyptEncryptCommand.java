package com.ject.oogachaka.global.config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.jasypt.encryption.StringEncryptor;

/**
 * Spring 애플리케이션과 DB를 시작하지 않고 설정값 한 줄을 암호화한다.
 * 비밀번호는 환경변수 JASYPT_ENCRYPTOR_PASSWORD를 먼저 보고, 없으면 application-local.properties에서 읽는다.
 */
public final class JasyptEncryptCommand {

    static final String LOCAL_CONFIG_PROPERTY = "jasypt.localConfig";

    private JasyptEncryptCommand() {
    }

    public static void main(String[] args) throws Exception {
        StringEncryptor encryptor = JasyptEncryptorFactory.create(resolvePassword());
        String value;
        if (System.console() != null) {
            char[] input = System.console().readPassword("암호화할 값: ");
            value = input == null ? null : new String(input);
            if (input != null) {
                java.util.Arrays.fill(input, '\0');
            }
        } else {
            value = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        }
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("암호화할 값 한 줄을 입력하세요.");
        }
        System.out.println("ENC(" + encryptor.encrypt(value) + ")");
    }

    private static String resolvePassword() throws Exception {
        String password = System.getenv(JasyptEncryptorFactory.PASSWORD_ENV);
        if (password != null && !password.isBlank()) {
            return password;
        }
        String localConfig = System.getProperty(LOCAL_CONFIG_PROPERTY);
        if (localConfig != null && Files.isRegularFile(Path.of(localConfig))) {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(Path.of(localConfig), StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            password = properties.getProperty(JasyptEncryptorFactory.PASSWORD_PROPERTY);
            if (password != null && !password.isBlank()) {
                return password;
            }
        }
        throw new IllegalStateException("Jasypt 비밀번호가 없습니다. src/main/resources/application-local.properties에 "
            + JasyptEncryptorFactory.PASSWORD_PROPERTY + "를 적거나 환경변수 "
            + JasyptEncryptorFactory.PASSWORD_ENV + "를 설정하세요.");
    }
}
