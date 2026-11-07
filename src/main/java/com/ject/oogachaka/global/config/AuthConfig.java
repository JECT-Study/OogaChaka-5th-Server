package com.ject.oogachaka.global.config;

import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// [검토] 설정 빈 정의를 등록한다. @Bean 메서드끼리 직접 호출하지 않으므로 메서드 프록시를 만들 필요가 없는 현재 구성이다.
@Configuration(proxyBeanMethods = false)
// [검토] 설정값 객체 세 개를 Spring 바인딩 대상으로 등록해 생성자 검증을 시작 단계에 수행한다.
@EnableConfigurationProperties({JwtProperties.class, OAuthProperties.class, CorsProperties.class})
// [검토] 인증 전용 시계·외부 HTTP 클라이언트·설정 바인딩을 구성한다.
public class AuthConfig {

    // [검토] 반환 객체를 Spring 빈으로 등록해 서비스들이 생성자에서 같은 구성을 주입받게 한다.
    @Bean
    // [검토] 현재 시각을 전역 정적 호출에 묶지 않고 테스트에서 고정 시계로 대체할 수 있게 한다.
    public Clock authClock() {
        // [검토] 토큰 계산은 시간대와 무관한 UTC Instant를 기준으로 한다. 운영 서버 사이 시각 동기화까지 해결하는 기능은 아니다.
        return Clock.systemUTC();
    }

    // [검토] 반환 객체를 Spring 빈으로 등록해 서비스들이 생성자에서 같은 구성을 주입받게 한다.
    @Bean
    // [검토] 소셜 제공자 코드·토큰 교환에만 쓰는 HTTP 클라이언트를 구성한다. 다른 업무의 관측·로깅 설정과 분리하려는 선택이다.
    public RestClient socialRestClient(OAuthProperties properties) {
        // [검토] TCP 연결이 성립하지 않는 제공자를 무한 대기하지 않도록 설정한 연결 시간 한도를 적용한다.
        HttpClient client = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
        // [검토] 별도 Apache HTTP 라이브러리 없이 JDK HttpClient를 RestClient에 연결하는 요청 팩토리다.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        // [검토] 연결 뒤 응답 대기도 설정한 시간으로 제한한다. 시간 값 5초 등은 현재 프로젝트의 운영 기본값이다.
        factory.setReadTimeout(properties.getReadTimeout());
        // 인증 코드/토큰을 관측 이벤트나 요청 로깅 인터셉터에 전달하지 않는 전용 클라이언트다.
        // [검토] Boot의 공용 Builder 대신 새 Builder를 사용해 공통 인터셉터·관측 설정이 인증 원문을 수집하지 않도록 분리했다.
        return RestClient.builder().requestFactory(factory).build();
    }
}
