package com.ject.oogachaka.global.config;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class JasyptConfig {

    @Bean("jasyptStringEncryptor")
    public StringEncryptor jasyptStringEncryptor(Environment environment) {
        // 비밀번호 출처(환경변수/로컬 파일) 검증을 CLI와 같은 팩토리 규칙으로 처리한다.
        return JasyptEncryptorFactory.create(JasyptEncryptorFactory.requirePassword(environment));
    }
}
