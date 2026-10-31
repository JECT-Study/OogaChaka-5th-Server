package com.ject.oogachaka.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.ject.oogachaka.notification.infrastructure.FcmPushSender;
import com.ject.oogachaka.notification.infrastructure.NoOpPushSender;
import com.ject.oogachaka.notification.service.PushSender;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FcmProperties.class)
public class FcmConfig {

    @Bean(destroyMethod = "delete")
    @ConditionalOnProperty(prefix = "app.fcm", name = "enabled", havingValue = "true")
    public FirebaseApp pushFirebaseApp(FcmProperties properties) {
        boolean hasPath = !properties.getServiceAccountPath().isBlank();
        boolean hasJson = !properties.getServiceAccountJson().isBlank();
        if (hasPath && hasJson) {
            throw new IllegalStateException("FCM 서비스 계정은 path 또는 json 중 하나만 지정해야 합니다.");
        }
        try {
            FirebaseOptions.Builder options = FirebaseOptions.builder()
                .setCredentials(loadCredentials(properties, hasPath, hasJson))
                .setConnectTimeout(3000).setReadTimeout(10000).setWriteTimeout(10000);
            if (!properties.getProjectId().isBlank()) {
                options.setProjectId(properties.getProjectId());
            }
            return FirebaseApp.initializeApp(options.build(), "oogachaka-push");
        } catch (IOException | RuntimeException exception) {
            // 자격증명 경로나 JSON/개인키가 들어갈 수 있는 공급자 예외의 메시지/cause를 노출하지 않는다.
            throw new IllegalStateException("FCM 초기화에 실패했습니다. 프로젝트와 자격증명 설정을 확인해 주세요. 원인 유형="
                + exception.getClass().getSimpleName());
        }
    }

    @Bean("pushSender")
    @ConditionalOnProperty(prefix = "app.fcm", name = "enabled", havingValue = "true")
    public PushSender fcmPushSender(FirebaseApp pushFirebaseApp) {
        return new FcmPushSender(FirebaseMessaging.getInstance(pushFirebaseApp));
    }

    @Bean("pushSender")
    @ConditionalOnProperty(prefix = "app.fcm", name = "enabled", havingValue = "false", matchIfMissing = true)
    public PushSender noOpPushSender() {
        return new NoOpPushSender();
    }

    private GoogleCredentials loadCredentials(FcmProperties properties, boolean hasPath, boolean hasJson)
        throws IOException {
        if (hasPath) {
            try (InputStream input = Files.newInputStream(Path.of(properties.getServiceAccountPath()))) {
                return ServiceAccountCredentials.fromStream(input);
            }
        }
        if (hasJson) {
            try (InputStream input = new ByteArrayInputStream(properties.getServiceAccountJson()
                .getBytes(StandardCharsets.UTF_8))) {
                return ServiceAccountCredentials.fromStream(input);
            }
        }
        return GoogleCredentials.getApplicationDefault();
    }
}
