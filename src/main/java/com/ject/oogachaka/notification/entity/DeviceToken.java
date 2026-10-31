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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 디바이스별 푸시 수신 등록. 토큰 원문은 응답이나 문자열 표현에 노출하지 않는다. */
@Getter
@Entity
@Table(name = "device_token", uniqueConstraints =
    @UniqueConstraint(name = "uk_device_token_token_hash", columnNames = "token_hash"))
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

    public void synchronize(Long userId, DevicePlatform devicePlatform) {
        this.userId = userId;
        this.devicePlatform = devicePlatform;
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        // DB의 DATETIME(6) 정밀도에서도 재등록 이후의 삭제를 구분한다.
        this.lastSyncedAt = lastSyncedAt == null || now.isAfter(lastSyncedAt)
            ? now : lastSyncedAt.plusNanos(1000);
    }
}
