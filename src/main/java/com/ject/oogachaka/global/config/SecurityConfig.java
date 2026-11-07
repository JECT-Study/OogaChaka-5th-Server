package com.ject.oogachaka.global.config;

import com.ject.oogachaka.auth.security.AuthRequestMatchers;
import com.ject.oogachaka.auth.security.JsonSecurityHandler;
import com.ject.oogachaka.auth.security.JwtAuthenticationFilter;
import com.ject.oogachaka.auth.token.JwtTokenProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// [검토] Security 구성 빈을 등록한다. @Bean 메서드를 직접 서로 호출하지 않아 설정 클래스 프록시가 필요하지 않다.
@Configuration(proxyBeanMethods = false)
// [검토] 인증 API의 공개 범위와 보호 API의 JWT 요구, CORS 및 오류 응답 정책을 한 곳에 둔다.
public class SecurityConfig {

    // [검토] 반환한 필터·설정 객체를 Security와 다른 빈이 참조할 수 있도록 Spring에 등록한다.
    @Bean
    // [검토] HTTP 요청에 적용할 보안 체인을 구성하면서 JWT 검증기를 주입받는다.
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider tokens, JsonSecurityHandler errorHandler)
        // [검토] HttpSecurity 빌드 API의 예외 계약을 따르는 시작 단계 구성 메서드다.
        throws Exception {
        // [검토] 인증 자격을 자동 전송되는 세션 쿠키 대신 명시적 Authorization 헤더·본문 토큰으로 받는 현재 API 방식에 맞춘 선택이다. 쿠키 인증을 도입하면 재검토해야 한다.
        return http.csrf(AbstractHttpConfigurer::disable)
            // 기본 세션 로그아웃을 끈다. 로그아웃은 자체 API가 담당한다.
            .logout(AbstractHttpConfigurer::disable)
            // 인증 상태를 HTTP 세션에 저장하지 않는다. 각 요청이 access를 제출한다.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // [검토] 아래 CORS 빈을 Security 체인에 연결해 브라우저 사전 요청이 JWT 인증 전에 처리되게 한다.
            .cors(Customizer.withDefaults())
            // [검토] 인증 없음과 권한 거부의 응답을 같은 JSON 핸들러로 맞춘다.
            .exceptionHandling(errors -> errors.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler))
            // [검토] 경로 인가를 선언한다. 더 구체적인 공개 규칙을 보호 API 규칙보다 먼저 적용한다.
            .authorizeHttpRequests(authorize -> authorize
                // [검토] 로그인 시작·콜백 제출·갱신만 access 없이 허용한다. JWT 필터도 같은 매처를 사용한다.
                .requestMatchers(AuthRequestMatchers.PUBLIC_ENDPOINTS).permitAll()
                // [검토] 나머지 API는 인증된 사용자만 접근한다. 현재 역할별 접근 제어는 구현하지 않았다.
                .requestMatchers("/api/**").authenticated()
                // [검토] API 밖 경로는 공개한다는 현재 선택이다. 나중에 API 밖 민감한 엔드포인트를 추가하면 별도 인가 규칙이 필요하다.
                .anyRequest().permitAll())
            // Servlet 필터 빈으로 등록하지 않아 Security 체인에서만 한 번 실행한다.
            // [검토] 인가 판단 전에 JWT 인증 주체를 만든다. 필터를 별도 Servlet 빈으로 등록하지 않아 체인 밖 중복 실행을 피한다.
            .addFilterBefore(new JwtAuthenticationFilter(tokens, errorHandler), UsernamePasswordAuthenticationFilter.class)
            // [검토] 구성한 Security 체인을 완성한다. 이후 실제 요청은 이 체인을 통과한다.
            .build();
    }

    // [검토] 반환한 필터·설정 객체를 Security와 다른 빈이 참조할 수 있도록 Spring에 등록한다.
    @Bean
    // [검토] 환경별 출처 목록을 실제 HTTP CORS 규칙으로 변환한다.
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        // [검토] 출처·메서드·허용 헤더를 명시할 빈 CORS 규칙 객체를 만든다.
        CorsConfiguration configuration = new CorsConfiguration();
        // [검토] 설정에 등록된 정확한 출처만 허용한다. localhost 기본값이 운영 서비스 출처까지 자동 허용하지는 않는다.
        configuration.setAllowedOrigins(properties.allowedOrigins());
        // [검토] API에서 사용할 HTTP 메서드를 미리 허용한 선택이다. 아직 없는 기능의 인가를 풀거나 컨트롤러를 생성하는 것은 아니다.
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // [검토] 프런트의 토큰·JSON·응답 형식 헤더를 허용한다. 다른 커스텀 헤더를 추가하면 이 목록도 검토해야 한다.
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        // [검토] 성공한 preflight 결과를 브라우저가 1시간 캐시하게 한 성능 기본값이다. 인증 토큰 수명과 관계없다.
        configuration.setMaxAge(3600L);
        // [검토] URL 범위별로 CORS 규칙을 찾는 제공 객체를 만든다.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // [검토] CORS 규칙을 API 경로에 한정한다. API 밖 경로의 출처 정책은 별도 범위다.
        source.registerCorsConfiguration("/api/**", configuration);
        // [검토] Security의 CORS 필터가 사용할 경로별 규칙을 반환한다.
        return source;
    }
}
