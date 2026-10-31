package com.ject.oogachaka.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 서비스 계정 비밀값을 문자열 표현에 포함하지 않는다. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.fcm")
public class FcmProperties {

    private boolean enabled;
    private String projectId = "";
    private String serviceAccountPath = "";
    private String serviceAccountJson = "";
}
