package com.ject.oogachaka.global.config;

import org.hibernate.cfg.ManagedBeanSettings;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoConfig {

    @Bean
    public HibernatePropertiesCustomizer encryptionBeanContainerCustomizer(ConfigurableListableBeanFactory beanFactory) {
        // Hibernate가 Converter를 직접 생성할 때도 Spring 생성자 주입을 사용한다.
        return properties -> properties.put(ManagedBeanSettings.BEAN_CONTAINER, new SpringBeanContainer(beanFactory));
    }
}
