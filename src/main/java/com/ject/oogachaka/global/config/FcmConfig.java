package com.ject.oogachaka.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.ject.oogachaka.notification.infrastructure.FcmPushSender;
import com.ject.oogachaka.notification.infrastructure.NoOpPushSender;
import com.ject.oogachaka.notification.service.PushSender;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** app.fcm.enabled=false(기본값)이면 Firebase 키 없이도 앱이 뜨도록 로그만 남기는 전송기를 쓴다. */
@Configuration(proxyBeanMethods = false)
public class FcmConfig {

    // devtools 재시작 때 같은 이름의 FirebaseApp이 남지 않도록 종료 시 삭제한다.
    @Bean(destroyMethod = "delete")
    @ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "true")
    public FirebaseApp firebaseApp(@Value("${app.fcm.service-account-json}") String serviceAccountJson)
        throws IOException {
        GoogleCredentials credentials = GoogleCredentials.fromStream(
            new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));
        return FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).build());
    }

    @Bean
    @ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "true")
    public PushSender fcmPushSender(FirebaseApp firebaseApp) {
        return new FcmPushSender(FirebaseMessaging.getInstance(firebaseApp));
    }

    @Bean
    @ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "false", matchIfMissing = true)
    public PushSender noOpPushSender() {
        return new NoOpPushSender();
    }
}
