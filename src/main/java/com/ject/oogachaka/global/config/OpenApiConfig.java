package com.ject.oogachaka.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    // 보호 API 문서에 붙이는 @SecurityRequirement(name = "bearer")가 이 이름을 가리킨다.
    public static final String BEARER = "bearer";

    // Swagger UI의 Authorize 버튼에 access 토큰을 넣으면 보호 API에 Authorization: Bearer 헤더가 붙는다.
    // 서버 주소는 상대 경로로 둔다. 자동 생성 주소는 HTTPS 프록시 뒤에서 http://로 잡혀 브라우저가 Try it out 요청을 막는다.
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info().title("oogachaka API").version("v1"))
            .servers(List.of(new Server().url("/")))
            .components(new Components().addSecuritySchemes(BEARER,
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
