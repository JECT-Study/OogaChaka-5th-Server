package com.ject.oogachaka.global.config;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.jasypt.salt.RandomSaltGenerator;

/**
 * 설정 복호화와 팀원용 암호화 도구에서 동일한 Jasypt 규격을 사용한다.
 */
final class JasyptEncryptorFactory {

    private JasyptEncryptorFactory() {
    }

    static StandardPBEStringEncryptor createFromEnvironment() {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        encryptor.setPassword(requirePassword());
        encryptor.setKeyObtentionIterations(10000);
        encryptor.setSaltGenerator(new RandomSaltGenerator());
        encryptor.setIvGenerator(new RandomIvGenerator());
        encryptor.setStringOutputType("base64");
        encryptor.initialize();
        return encryptor;
    }

    static String requirePassword() {
        // 프로퍼티나 JVM 인자로 암호화 비밀번호를 대체하지 않는다.
        String password = System.getenv("JASYPT_ENCRYPTOR_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("환경변수 JASYPT_ENCRYPTOR_PASSWORD가 필요합니다.");
        }
        return password;
    }
}
