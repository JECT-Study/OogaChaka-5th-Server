package com.ject.oogachaka.global.config;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class JasyptConfig {

    @Bean("jasyptStringEncryptor")
    public StringEncryptor jasyptStringEncryptor(Environment environment) {
        // 빈 생성 시 비밀번호 누락과 공통 설정 파일에 비밀번호가 정의되었는지 검사한다.
        return JasyptEncryptorFactory.create(JasyptEncryptorFactory.requirePassword(environment));
    }
}
