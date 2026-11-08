package com.ject.oogachaka.auth.entity;

import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 원본 소셜 ID와 제공자 토큰을 저장하지 않는 소셜 계정 */
@Getter
@Entity
@Table(name = "social_account", uniqueConstraints = {
    @UniqueConstraint(name = "uk_social_account_provider_user", columnNames = {"auth_provider", "provider_user_id_hash"}),
    @UniqueConstraint(name = "uk_social_account_user", columnNames = "user_id")
})
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SocialAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private AuthProvider authProvider;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String providerUserIdHash;

    @Column(length = 255)
    private String email;

    public static SocialAccount create(Long userId, AuthProvider provider, String providerUserIdHash, String email) {
        return SocialAccount.builder()
                .userId(userId)
                .authProvider(provider)
                .providerUserIdHash(providerUserIdHash)
                .email(email)
                .build();
    }
}
