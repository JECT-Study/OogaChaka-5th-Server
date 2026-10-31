package com.ject.oogachaka.global.config;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JasyptConfig {

    @Bean("jasyptStringEncryptor")
    public StringEncryptor jasyptStringEncryptor() {
        // starter의 프로퍼티/JVM 인자 비밀번호 대신 환경변수만 읽는 암호화기를 제공한다.
        return JasyptEncryptorFactory.createFromEnvironment();
    }
}
