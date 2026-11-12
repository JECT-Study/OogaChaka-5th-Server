package com.ject.oogachaka.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    // 보호 API 문서에 붙이는 @SecurityRequirement(name = "bearer")가 이 이름을 가리킨다.
    public static final String BEARER = "bearer";

    // Swagger UI의 Authorize 버튼에 access 토큰을 넣으면 보호 API에 Authorization: Bearer 헤더가 붙는다.
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info().title("oogachaka API").version("v1"))
            .components(new Components().addSecuritySchemes(BEARER,
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
