package com.ject.oogachaka.global.config;

import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({JwtProperties.class, OAuthProperties.class, CorsProperties.class})
public class AuthConfig {

    @Bean
    public Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    public RestClient socialRestClient(OAuthProperties properties) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().requestFactory(factory).build();
    }
}
