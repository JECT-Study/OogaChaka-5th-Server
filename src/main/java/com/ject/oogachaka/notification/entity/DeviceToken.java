package com.ject.oogachaka.notification.entity;

import com.ject.oogachaka.global.crypto.EncryptionConverter;
import com.ject.oogachaka.global.entity.BaseTimeEntity;
import com.ject.oogachaka.notification.enums.DevicePlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 기기별 푸시 수신 등록. 토큰 원문은 AES로 암호화해 저장하고, 중복 검사는 token_hash로 한다. */
@Getter
@Entity
@Table(name = "device_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Convert(converter = EncryptionConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String token;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private DevicePlatform devicePlatform;

    @Column(nullable = false)
    private LocalDateTime lastSyncedAt;

    private DeviceToken(Long userId, String token, String tokenHash, DevicePlatform devicePlatform) {
        this.token = token;
        this.tokenHash = tokenHash;
        synchronize(userId, devicePlatform);
    }

    public static DeviceToken create(Long userId, String token, String tokenHash, DevicePlatform devicePlatform) {
        return new DeviceToken(userId, token, tokenHash, devicePlatform);
    }

    // 같은 기기에서 다른 계정으로 로그인하면 소유자가 바뀐다.
    public void synchronize(Long userId, DevicePlatform devicePlatform) {
        this.userId = userId;
        this.devicePlatform = devicePlatform;
        this.lastSyncedAt = LocalDateTime.now();
    }
}
