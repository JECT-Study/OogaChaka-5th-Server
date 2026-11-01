package com.ject.oogachaka.global.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 설정값 사용 여부와 관계없이 비밀번호 누락을 컨텍스트 생성 전에 알린다. 복호화는 starter가 담당한다.
 * 설정 파일(application-{profile}.properties)을 읽은 뒤 실행되어 로컬 파일의 비밀번호도 확인한다.
 */
public class JasyptPasswordEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        JasyptEncryptorFactory.requirePassword(environment);
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
