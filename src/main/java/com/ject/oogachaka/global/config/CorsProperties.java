package com.ject.oogachaka.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins, boolean allowCredentials) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()
            || allowedOrigins.stream().anyMatch(origin -> origin == null || origin.isBlank() || origin.contains("*"))) {
            throw new IllegalStateException("CORS에는 명시적인 허용 origin 목록이 필요합니다.");
        }
        allowedOrigins = List.copyOf(allowedOrigins);
    }
}
